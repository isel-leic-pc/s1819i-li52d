using System;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using ConsoleApp1.Logging;

namespace ConsoleApp1.Sketches
{
    public class TcpServerExample
    {
        private static readonly ILog Log = LogProvider.For<TcpServerExample>();
        
        public static async Task EchoServer()
        {
            const int port = 8081;
            var listener = new TcpListener(IPAddress.Loopback, port);
            listener.Start();
            Log.Info($"started listening in {port}");
            var id = 0;
            while (true)
            {
                var client = await listener.AcceptTcpClientAsync();
                Echo(client, id++);

            }
        }

        private static async Task Echo(TcpClient client, int id)
        {
            Log.Info($"[{id}]: started echo ");
            var buffer = new byte[4 * 1024];
            var stream = client.GetStream();
            client.ReceiveTimeout = 1000;
            while (true)
            {
                var cts = new CancellationTokenSource();
                cts.CancelAfter(TimeSpan.FromMilliseconds(1000));
                var ct = cts.Token;
                var len = await stream.ReadAsync(buffer, 0, buffer.Length, ct);
                if (len == 0)
                {
                    Log.Info($"[{id}]: stream ended, ending. Bye.");
                    return;
                }
                Log.Info($"[{id}]: echoing {len} bytes");
                await stream.WriteAsync(buffer, 0, len);
            } 
        }
    }
}