using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Examples
{
    public struct TimeoutInstant
    {
        private readonly int target;
        public TimeoutInstant(int timeout)
        {
            target = timeout == Timeout.Infinite
                ? target = -1
                : Environment.TickCount + timeout;
        }

        public int Remaining
        {
            get
            {
                return target == -1
                    ? Timeout.Infinite
                    : target - Environment.TickCount;
            }
        }

        public bool IsTimeout
        {
            get
            {
                return Remaining <= 0;
            }
        }

        public static bool ShouldWait(int timeout)
        {
            return timeout != 0;
        }
    }
}
