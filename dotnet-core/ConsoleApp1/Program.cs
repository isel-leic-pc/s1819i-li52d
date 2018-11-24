using System;
using System.Threading.Tasks;
using ConsoleApp1.Examples;
using Serilog;

namespace ConsoleApp1
{
    class Program
    {
        static async Task Main(string[] args)
        {
            var log = new LoggerConfiguration()
                .WriteTo.ColoredConsole(outputTemplate: "{Timestamp:HH:mm} [{Level}] ({Name:l}) {Message}{NewLine}{Exception}")
                .CreateLogger();
            Log.Logger = log;
            
            await EchoTcpServerProgram.Run();
        }
    }
}