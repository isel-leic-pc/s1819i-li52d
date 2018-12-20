using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace ConsoleApp1.Sketches
{
    public class AsyncSemaphore
    {
        private readonly int _maxUnits;

        private int _units;
        private readonly object _mon = new object();
        private readonly LinkedList<Consumer> _consumers = new LinkedList<Consumer>();

        private static readonly Task<bool> trueTask = Task.FromResult(true);
        private static readonly Task<bool> falseTask = Task.FromResult(false);

        // for debugging purposes
        private readonly Counter _successRace;
        private readonly Counter _timeoutRace;
        private readonly Counter _cancelRace;
        private readonly Counter _reentrancyCounter;
        private readonly ThreadLocal<int> _tls = new ThreadLocal<int>(() => 0);

        private class Consumer : ConsumerBase<bool>
        {
            public int Units { get; }

            public Consumer(int units)
            {
                Units = units;
            }

            public Timer Timer { get; set; }
            public CancellationTokenRegistration CancellationRegistration { get; set; }
        }

        public AsyncSemaphore(
            int maxUnits,
            Counter successRace, Counter timeoutRace, Counter cancelRace, Counter reentrancyCounter)
        {
            _units = maxUnits;
            _successRace = successRace;
            _timeoutRace = timeoutRace;
            _cancelRace = cancelRace;
            _reentrancyCounter = reentrancyCounter;
        }

        public Task<bool> Acquire(int requestedUnits, TimeSpan timeout, CancellationToken ct)
        {
            try
            {
                _tls.Value += 1;
                _reentrancyCounter.Ceiling(_tls.Value);
                lock (_mon)
                {
                    if (_consumers.Count == 0 && _units >= requestedUnits)
                    {
                        _units -= requestedUnits;
                        return trueTask;
                    }

                    if (timeout.TotalMilliseconds <= 0)
                    {
                        return falseTask;
                    }

                    var consumer = new Consumer(requestedUnits);
                    var node = _consumers.AddLast(consumer);

                    consumer.Timer = new Timer(CancelDueToTimeout, node, timeout, new TimeSpan(-1));
                    consumer.CancellationRegistration = ct.Register(CancelDueToCancellationToken, node);

                    return consumer.Task;
                }
            }
            finally
            {
                _tls.Value -= 1;
            }
        }

        private void CancelDueToTimeout(object state)
        {
            var node = state as LinkedListNode<Consumer>;

            if (!node.Value.TryAcquire())
            {
                _timeoutRace.Increment();
                return;
            }

            List<Consumer> consumersToComplete;
            lock (_mon)
            {
                node.Value.CancellationRegistration.Dispose();
                _consumers.Remove(node);
                consumersToComplete = ReleaseAllWithinLock();
            }

            node.Value.SetResult(false);
            foreach (var consumer in consumersToComplete)
            {
                consumer.SetResult(true);
            }
        }

        private void CancelDueToCancellationToken(object state)
        {
            var node = state as LinkedListNode<Consumer>;

            if (!node.Value.TryAcquire())
            {
                _cancelRace.Increment();
                return;
            }

            node.Value.Timer.Dispose();
            List<Consumer> consumersToComplete;
            lock (_mon)
            {
                _consumers.Remove(node);
                consumersToComplete = ReleaseAllWithinLock();
            }

            node.Value.SetCanceled();
            foreach (var consumer in consumersToComplete)
            {
                consumer.SetResult(true);
            }
        }

        public void Release(int releaseUnits)
        {
            try
            {
                _tls.Value += 1;
                _reentrancyCounter.Ceiling(_tls.Value);

                List<Consumer> consumersToComplete;
                lock (_mon)
                {
                    _units += releaseUnits;
                    consumersToComplete = ReleaseAllWithinLock();
                }

                foreach (var consumer in consumersToComplete)
                {
                    consumer.SetResult(true);
                }
            }
            finally
            {
                _tls.Value -= 1;
            }
        }

        private List<Consumer> ReleaseAllWithinLock()
        {
            var consumersToComplete = new List<Consumer>();
            while (_consumers.Count() != 0 && _units >= _consumers.First.Value.Units)
            {
                var first = _consumers.First;
                if (!first.Value.TryAcquire())
                {
                    _successRace.Increment();
                    break;
                }

                _consumers.RemoveFirst();
                first.Value.Timer.Dispose();
                first.Value.CancellationRegistration.Dispose();
                _units -= first.Value.Units;
                consumersToComplete.Add(first.Value);
            }

            return consumersToComplete;
        }
    }
}