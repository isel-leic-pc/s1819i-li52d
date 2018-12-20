using System.Threading.Tasks;
using ConsoleApp1.Examples;
using ConsoleApp1.Logging;
using ConsoleApp1.Sketches;
using Serilog;

namespace ConsoleApp1
{
    class Program
    {
//        static async Task Main(string[] args)
//        {
//            var log = new LoggerConfiguration()
//                .WriteTo.ColoredConsole(outputTemplate: "{Timestamp:HH:mm} [{Level}] ({Name:l}) {Message}{NewLine}{Exception}")
//                .CreateLogger();
//            Log.Logger = log;
//            
//            await EchoTcpServerProgram.Run();
//        }

        private static readonly ILog Logger = LogProvider.For<Program>();
        
        static void Main(string[] args)
        {
            var log = new LoggerConfiguration()
                .Enrich.WithThreadId()
                .WriteTo.ColoredConsole(outputTemplate: "{Timestamp:HH:mm} [{Level}][th:{ThreadId}] ({Name:l}) {Message}{NewLine}{Exception}")
                .CreateLogger();
                Log.Logger = log;
            
            // TaskExamples.ReadFile();
            // TaskExamples.ReadSocket();
            
            //HttpClientExamples.First();
            //HttptClientExamples.RequestsInSequence();
            // Task t = HttpClientExamples.RequestsInSequence3();
            //Logger.Info("main is ending");
            //return t;

            //Logger.Info("start");
            //Task<int> t = AsyncAwaitExamples.First();
            //Task<int> t = AsyncAwaitExamples.Second();
            //Logger.Info($"Task status is '{t.Status}'");
            //Logger.Info("end");

            // return TcpServerExample.EchoServer();
            TestAsyncSemaphore.Run();
            
        }
    }
}