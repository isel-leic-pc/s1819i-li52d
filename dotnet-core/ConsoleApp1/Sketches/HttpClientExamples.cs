using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using ConsoleApp1.Logging;

namespace ConsoleApp1.Sketches
{
    public class HttpClientExamples
    {
        private static readonly ILog Log = LogProvider.For<HttpClientExamples>();

        private static readonly string[] urls =
        {
            "http://httpbin.org/delay/2",
            "http://httpbin.org/delay/2",
            "http://httpbin.org/delay/2",
            "http://httpbin.org/delay/2",
        };

        public static void First()
        {
            Log.Info("start");
            var sem = new SemaphoreSlim(0);

            var client = new HttpClient();
            var responseTask = client.GetAsync("http://httpbin.org/get");
            responseTask.ContinueWith(t =>
            {
                Log.Info("on continuation");
                if (t.IsCompletedSuccessfully)
                {
                    var response = t.Result;
                    Log.Info($"Response received with status {response.StatusCode}");
                }
                else
                {
                    Log.Warn($"Error while getting response: {t.Exception.Message}");
                }

                sem.Release(1);
            });
            sem.Wait();
        }

        public static void RequestsInSequence()
        {
            Log.Info("start");
            var sem = new SemaphoreSlim(0);

            var client = new HttpClient();
            var responseTask = client.GetAsync(urls[0]);
            for (var i = 1; i < urls.Length; ++i)
            {
                var ix = i;
                responseTask = responseTask
                    .ContinueWith(t =>
                    {
                        if (t.IsCompletedSuccessfully)
                        {
                            var response = t.Result;
                            Log.Info($"Response received with status {response.StatusCode}");
                        }
                        else
                        {
                            Log.Warn($"Error while getting response: {t.Exception.Message}");
                        }

                        return client.GetAsync(urls[ix]);
                    })
                    .Unwrap();
            }

            responseTask.ContinueWith(t =>
            {
                if (t.IsCompletedSuccessfully)
                {
                    var response = t.Result;
                    Log.Info($"Response received with status {response.StatusCode}");
                }
                else
                {
                    Log.Warn($"Error while getting response: {t.Exception.Message}");
                }

                sem.Release(1);
            });

            sem.Wait();
        }

        public static Task RequestsInSequence2()
        {
            Log.Info("start");
            TaskCompletionSource<object> tcs = new TaskCompletionSource<object>();


            var client = new HttpClient();

            void doRequest(int ix)
            {
                client.GetAsync(urls[ix]).ContinueWith(t =>
                {
                    if (t.IsCompletedSuccessfully)
                    {
                        var response = t.Result;
                        Log.Info($"Response received with status {response.StatusCode}");
                    }
                    else
                    {
                        Log.Warn($"Error while getting response: {t.Exception.Message}");
                    }

                    if (t.IsCompletedSuccessfully && (ix + 1) < urls.Length)
                    {
                        doRequest(ix + 1);
                    }
                    else
                    {
                        tcs.SetResult(null);
                    }
                });
            }

            doRequest(0);
            Log.Info("RequestsInSequence2 ending");
            return tcs.Task;
        }

        public static async Task<int> RequestsInSequence3()
        {
            Log.Info("RequestsInSequence3 started");
            var client = new HttpClient();
            HttpResponseMessage response = null;
            foreach (var url in urls)
            {
                try
                {
                    Log.Info($"Doing request to {url}");
                    response = await client.GetAsync(url);
                    Log.Info($"Response received with status {response.StatusCode}");

                    Task.Factory.StartNew(() => Thread.Sleep(2000));
                }
                catch (Exception e)
                {
                    Log.Warn($"Error while getting response: {e.Message}");
                    break;
                }
            }

            Log.Info("RequestsInSequence3 is ending");
            return (int) response.StatusCode;
        }

        public static async Task<int> RequestsInParallel1()
        {
            Log.Info("RequestsInSequence3 started");
            var client = new HttpClient();
            List<Task<HttpResponseMessage>> responses = new List<Task<HttpResponseMessage>>();
            foreach (var url in urls)
            {
                Log.Info($"Doing request to {url}");
                responses.Add(client.GetAsync(url));
            }

            int lastStatus = 0;
            foreach (var t in responses)
            {
                try
                {
                    var response = await t;
                    Log.Info($"Response received with status {response.StatusCode}");
                    lastStatus = (int) response.StatusCode;
                }
                catch (Exception e)
                {
                    Log.Warn($"Error while getting response: {e.Message}");
                    // break;
                    throw;
                }
            }

            Log.Info("RequestsInParallel1 ending");
            return lastStatus;
        }
    }
}