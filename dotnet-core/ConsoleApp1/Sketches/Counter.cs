using System.Threading;

namespace ConsoleApp1.Sketches
{
    public class Counter
    {
        private readonly string name;
        private volatile int counter;

        public Counter(string name)
        {
            this.name = name;
        }

        public void Increment()
        {
            Interlocked.Increment(ref counter);
        }

        public void Ceiling(int value)
        {
            do
            {
                var observed = counter;
                if (value <= observed) return;
                if (Interlocked.CompareExchange(ref counter, value, observed) == observed)
                {
                    return;
                }
            } while (true);
        }

        public override string ToString()
        {
            return $" {name}: {counter}";
        }
    }
}