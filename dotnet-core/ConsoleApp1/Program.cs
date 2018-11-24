using System;
using System.Threading.Tasks;
using ConsoleApp1.Examples;

namespace ConsoleApp1
{
    class Program
    {
        static async Task Main(string[] args)
        {
            await EchoTcpServerProgram.Run();
        }
    }
}