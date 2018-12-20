using System;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace ConsoleApp1.Sketches
{
    public static class TestAsyncSemaphore
    {
        private static readonly Counter successCounter = new Counter("success");
        private static readonly Counter timeoutCounter = new Counter("timeout");
        private static readonly Counter cancellationCounter = new Counter("cancellation");

        private static readonly Counter successRace = new Counter("successRace");
        private static readonly Counter timeoutRace = new Counter("timeoutRace");
        private static readonly Counter cancelRace = new Counter("cancelRace");
        private static readonly Counter reentrancyCounter = new Counter("reentrancy");

        private static readonly Counter[] counters =
        {
            successCounter, timeoutCounter, cancellationCounter,
            successRace, timeoutRace, cancelRace, reentrancyCounter
        };


        private const int maxUnits = 3;

        private static readonly AsyncSemaphore sem =
            new AsyncSemaphore(maxUnits, successRace, timeoutRace, cancelRace, reentrancyCounter);

        private static int grantedUnits = 0;
        private const int nOfFlows = 100;

        public static void Run()
        {
            for (var i = 0; i < nOfFlows; ++i)
            {
                Task.Factory.StartNew(Flow);
            }

            for (;;)
            {
                Thread.Sleep(1000);
                Console.WriteLine(counters
                    .Select(c => c.ToString())
                    .Aggregate((s1, s2) => s1 + s2));
            }
        }

        private static async void Flow()
        {
            var r = new Random();
            for (;;)
            {
                var units = (r.Next() % (maxUnits - 1)) + 1;

                var timeoutTime = TimeSpan.FromMilliseconds(r.Next() % 100);
                var cancellationTime = r.Next() % 100;
                var cts = new CancellationTokenSource();
                cts.CancelAfter(cancellationTime);
                try
                {
                    if (r.Next() % 5 == 0)
                    {
                        // for 1 in N, the token is already cancelled when entering the Acquire
                        cts.Cancel();
                    }

                    var result = await sem.Acquire(units, timeoutTime, cts.Token);
                    if (result == false)
                    {
                        timeoutCounter.Increment();
                        continue;
                    }

                    successCounter.Increment();
                    var currentlyGranted = Interlocked.Add(ref grantedUnits, units);
                    if (currentlyGranted > maxUnits)
                    {
                        Console.WriteLine($"ERROR: granted {currentlyGranted} and max is {maxUnits}");
                        Environment.Exit(1);
                    }

                    if (r.Next() % 2 == 0)
                    {
                        await Task.Delay(r.Next() % 10);
                    }

                    Interlocked.Add(ref grantedUnits, -units);
                    sem.Release(units);
                }
                catch (Exception e)
                {
                    cancellationCounter.Increment();
                }
            }
        }
    }
}