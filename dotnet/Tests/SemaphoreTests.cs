using Examples;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Xunit;

namespace Tests
{
    public class SemaphoreTests
    {
        [Fact]
        public void Test()
        {
            int nOfThreads = 4;
            int nOfReps = 10;
            Helper h = new Helper();
            var semaphore = new SemaphoreFifo(nOfThreads);
            long units = nOfThreads;
            for(int i = 0; i<nOfThreads; ++i)
            {
                h.CreateAndStart(() =>
                {
                    for (int j = 0; j < nOfReps; ++j)
                    {
                        Console.WriteLine("before Acquire");
                        semaphore.Acquire(2, Timeout.Infinite);
                        Interlocked.Add(ref units, -2);
                        Assert.True(Interlocked.Read(ref units) >= 0);
                        Thread.Sleep(10);
                        Interlocked.Add(ref units, 2);
                        semaphore.Release(2);
                    }
                });
            }
            h.Join(TimeSpan.FromMinutes(1));
        }
    }
}
