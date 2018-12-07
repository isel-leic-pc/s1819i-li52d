using System;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using ConsoleApp1.Examples;
using ConsoleApp1.Logging;

namespace ConsoleApp1.Sketches
{
    public class TaskExamples
    {
        private static readonly ILog Log = LogProvider.For<TaskExamples>();
        
        public static void ReadFile()
        {
            Log.Info("start");
            var sem = new SemaphoreSlim(0);
            var fs = new FileStream("Program.cs", FileMode.Open);
            var buffer = new byte[1024];
            Task<int> task = fs.ReadAsync(buffer, 0, buffer.Length);
            task.ContinueWith(t =>
            {
                int i = t.Result;
                Log.Info($"read completed with {i} bytes");
                sem.Release();
            });
            sem.Wait();
            Log.Info("end");
        }
        
        public static void ReadSocket()
        {
            Log.Info("start");
            var sem = new SemaphoreSlim(0);
            var client = new TcpClient();
            
            Task task = client.ConnectAsync("localhost", 81);
            var request = "GET https://httpbin.org/get HTTP/1.1\r\n"
                          + "User-Agent: Fiddler\r\nHost: httpbin.org\r\n\r\n";
            var bytes = Encoding.UTF8.GetBytes(request);
            var buffer = new byte[1024];
            task.ContinueWith(t =>
                {
                    Log.Info($"cw1, status = {t.Status}");
                    if (t.IsFaulted)
                    {    
                        Log.Info($"cw1, exc = {t.Exception.Message}");
                        throw new Exception("cw1 exception");
                    }
                    // throw new Exception("an-error");
                    return client.GetStream().WriteAsync(bytes, 0, bytes.Length);
                })
                .Unwrap()
                .ContinueWith(t =>
                {
                    Log.Info($"cw2, status = {t.Status}");
                    if (t.IsFaulted)
                    {
                        Log.Info($"cw2, exc = {t.Exception.Message}");
                        throw t.Exception;
                    }
                    return client.GetStream().ReadAsync(buffer, 0, buffer.Length);
                })
                .Unwrap()
                .ContinueWith(t =>
                {
                    Log.Info($"cw3, status = {t.Status}");
                    try
                    {
                        var len = t.Result;
                        Log.Info($"read {len} bytes");
                        var s = Encoding.UTF8.GetString(buffer, 0, len);
                        return s;
                    }
                    catch (Exception e)
                    {
                        Log.Warn($"Exception is {e.Message}");
                        throw;
                    }
                })
                .ContinueWith(t =>
                {
                    Log.Info($"cw4, status = {t.Status}");
                    if (t.IsCompletedSuccessfully)
                    {
                        Log.Info($"response is:\n{t.Result}");
                    }
                    else
                    {
                        Log.Warn($"errors was {t.Exception.InnerException.Message}");
                    }
                    sem.Release();
                });
            sem.Wait();
            Log.Info("end");
        }
    }
}