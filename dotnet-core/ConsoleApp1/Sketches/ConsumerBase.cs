using System.Threading;
using System.Threading.Tasks;

namespace ConsoleApp1.Sketches
{
    public class ConsumerBase<T> : TaskCompletionSource<T>
    {
        private int acquired = 0;

        public bool TryAcquire()
        {
            return Interlocked.CompareExchange(ref acquired, 1, 0) == 0;
        }
    }
}