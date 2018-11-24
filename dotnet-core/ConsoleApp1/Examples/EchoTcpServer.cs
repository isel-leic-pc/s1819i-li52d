using System;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using ConsoleApp1.Logging;

namespace ConsoleApp1.Examples
{    
    class EchoTcpServer
    {
        private static readonly ILog Log = LogProvider.For<EchoTcpServer>(); 
        
        private readonly Terminator terminator = new Terminator();

        public async Task Start(int port, CancellationToken ctoken)
        {
            var connectionId = 0;
            var ipAddress = Dns.GetHostEntry("localhost").AddressList[0];
            var listener = new TcpListener(ipAddress, port);

            // needed because there isn't another way to cancel the
            // AcceptTcpClientAsync
            using (ctoken.Register(() =>
            {
                Log.Info("stopping listener");
                listener.Stop();
            }))
            {
                try
                {
                    listener.Start();
                    Log.Info($"listening on {port}");
                    for (; !ctoken.IsCancellationRequested;)
                    {
                        Log.Info("accepting...");
                        var client = await listener.AcceptTcpClientAsync();
                        Log.Info("...client accepted");
                        connectionId += 1;
                        Echo(client, ctoken, connectionId);
                    }
                }
                catch (Exception e)
                {
                    // await AcceptTcpClientAsync will end up with an exception
                    Log.Info($"Exception '{e.Message}' received");
                }
            }

            await terminator.Shutdown();
        }

        private async void Echo(TcpClient client, CancellationToken ctoken, int connectionId)
        {
            const int cap = 1024;
            var buffer = new byte[cap];
            var stream = client.GetStream();
            var tcs = new TaskCompletionSource<int>();
            using (terminator.Enter())
            using (client)
            using (stream)
            using (ctoken.Register(() => tcs.SetCanceled()))
            {
                for (;;)
                {
                    try
                    {
                        var readBytes = await ReadFromStreamAsync(stream, buffer, ctoken, tcs.Task);
                        Log.Info($"[{connectionId}] read '{readBytes}' bytes");
                        if (readBytes == 0)
                        {
                            Log.Info($"[{connectionId}] ending.");
                            return;
                        }

                        await stream.WriteAsync(buffer, 0, readBytes, ctoken);
                    }
                    catch (TaskCanceledException e)
                    {
                        // This looses data if the read or write can cancelled
                        client.Close();
                        Log.Info($"[{connectionId}] cancelled: '{e}'");
                        return;
                    }
                }
            }
        }

        // Because the underlying socket implementation does not support cancellation
        private Task<int> ReadFromStreamAsync(
            NetworkStream stream,
            byte[] buffer,
            CancellationToken ctoken,
            Task<int> cancellationTask)
        {
            var readTask = stream.ReadAsync(buffer, 0, buffer.Length, ctoken);
            return Task.WhenAny(readTask, cancellationTask).Unwrap();
        }
    }

    // The example program
    class EchoTcpServerProgram
    {
        private static readonly ILog Log = LogProvider.For<EchoTcpServerProgram>(); 
        
        private static readonly CancellationTokenSource cts = new CancellationTokenSource();
        private static readonly Terminator terminator = new Terminator();

        public static async Task Run()
        {
            AppDomain.CurrentDomain.ProcessExit += HandleProcessExit;
            Console.CancelKeyPress += HandleCancel;

            EchoTcpServer server = new EchoTcpServer();
            using (terminator.Enter())
            {
                await server.Start(8081, cts.Token);
                Log.Info("ending, bye");
            }
        }

        private static void HandleCancel(object sender, ConsoleCancelEventArgs e)
        {
            e.Cancel = true;
            cts.Cancel();
            // The application will endup normally on the Main flow
        }

        private static void HandleProcessExit(object sender, EventArgs eventArgs)
        {
            Log.Info($"handling process exit");
            cts.Cancel();
            terminator.Shutdown().Wait();
        }
    }
}
