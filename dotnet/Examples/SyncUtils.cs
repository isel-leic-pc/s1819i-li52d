using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    public static class SyncUtils
    {
        public static void Wait(object mlock, object condition, int timeout)
        {
            if(mlock == condition)
            {
                Monitor.Wait(mlock, timeout);
            }            
            Monitor.Enter(condition);
            Monitor.Exit(mlock);
            try
            {
                Monitor.Wait(condition, timeout);
            }
            finally
            {
                Monitor.Exit(condition);
                bool wasInterrupted;
                EnterUninterruptibly(mlock, out wasInterrupted);
                if (wasInterrupted) {
                    throw new ThreadInterruptedException();
                }
            }
        }

        public static void Wait(object mlock, object condition)
        {
            Wait(mlock, condition, Timeout.Infinite);
        }

        public static void Notify(object mlock, object condition)
        {
            if(mlock == condition)
            {
                Monitor.Pulse(mlock);
                return;
            }
            bool wasInterrupted;
            EnterUninterruptibly(condition, out wasInterrupted);
            Monitor.Pulse(condition);
            Monitor.Exit(condition);
            if(wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void NotifyAll(object mlock, object condition)
        {
            if (mlock == condition)
            {
                Monitor.Pulse(mlock);
                return;
            }
            bool wasInterrupted;
            EnterUninterruptibly(condition, out wasInterrupted);
            Monitor.PulseAll(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void EnterUninterruptibly(object mon, out bool wasInterrupted)
        {
            wasInterrupted = false;
            while(true)
            {
                try
                {
                    Monitor.Enter(mon);
                    return;
                }
                catch (ThreadInterruptedException)
                {
                    wasInterrupted = true;
                }
            }
        }
    }
}
