using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.ExceptionServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Tests
{
    class Helper
    {
        private readonly List<Thread> ths = new List<Thread>();
        private readonly ConcurrentQueue<Exception> excs = new ConcurrentQueue<Exception>();

        public Thread CreateAndStart(ThreadStart action) {
            Thread th = new Thread(() => {
                try
                {
                    action();
                } catch (Exception e)
                {
                    excs.Enqueue(e);
                }
            });
            th.Start();
            ths.Add(th);
            return th;
        }

        public void Join(TimeSpan timeout)
        {            
            foreach(var th in ths)
            {
                bool wasTimeout = !th.Join(timeout);
                if(wasTimeout)
                {
                    th.Interrupt();
                    th.Join();
                }
                
            }            
            if(excs.Count != 0)
            {
                ExceptionDispatchInfo.Capture(excs.ElementAt(0)).Throw();
            }
        }
    }
}
