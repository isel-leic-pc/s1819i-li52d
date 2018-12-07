using System;
using System.Threading.Tasks;

namespace ConsoleApp1.Sketches
{
    public class AsyncAwaitExamples
    {
        public static Task<int> First()
        {
            throw new Exception();
            return Task.FromResult(1);
        }
        
        public static async Task<int> Second()
        {
            throw new Exception();
            return 1;
        }
    }
}