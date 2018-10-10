using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    public class SemaphoreFifo
    {
        private int units;

        private readonly LinkedList<int> q = new LinkedList<int>();
        private readonly object mutex = new object();

        public SemaphoreFifo(int initial)
        {
            units = initial;
        }

        public bool Acquire(int requestedUnits, int timeout)
        {
            lock (mutex)
            {
                if (units >= requestedUnits && q.Count == 0)
                {
                    units -= requestedUnits;
                    return true;
                }
                var node = q.AddLast(requestedUnits);
                TimeoutInstant limit = new TimeoutInstant(timeout);
                do
                {                     
                    try
                    {
                        SyncUtils.Wait(mutex, node, limit.Remaining);
                    }
                    catch (ThreadInterruptedException)
                    {
                        q.Remove(node);
                        NotifyIfNeeded();
                        throw;
                    }
                    if (node == q.First && units >= requestedUnits)
                    {
                        q.RemoveFirst();
                        units -= requestedUnits;
                        NotifyIfNeeded();
                        return true;
                    }
                    if (limit.IsTimeout)
                    {
                        q.Remove(node);
                        NotifyIfNeeded();
                        return false;
                    }

                } while (true);
            }
        }

        public void Release(int releasedUnits)
        {
            lock (mutex)
            {
                units += releasedUnits;
                NotifyIfNeeded();
            }
        }

        private void NotifyIfNeeded()
        {
            if (q.Count > 0 && units >= q.First.Value)
            {
                SyncUtils.Notify(mutex, q.First);                
            }
        }
    }
}
