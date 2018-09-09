package pt.isel.pc.examples.intro;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/*
 * Example to illustrates how different HTTP requests are handled on different threads.
 * Notice how the doGet method is called on the same instance but on different threads.
 *
 * Example usage: curl "http://localhost:8081/[1-100]"
 */

public class ServletExample {

    public static Logger log = LoggerFactory.getLogger(ServletExample.class);

    public static void main(String[] args) throws Exception {

        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");

        Server server = new Server(8081);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(new ServletHolder(new SimpleServlet()), "/*");
        server.start();
        log.info("Server started");
        server.join();
    }

    private static class SimpleServlet extends HttpServlet {

        private static final Charset charset = StandardCharsets.UTF_8;

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
            String name = Thread.currentThread().getName();
            log.info("handling request for {} on thread {}", req.getPathInfo(), name);
            writeToResponse(res, name);
        }

        private void writeToResponse(HttpServletResponse res, String s) throws IOException {
            byte[] bytes = s.getBytes(charset);
            res.setStatus(200);
            res.setContentType("text/plain");
            res.setCharacterEncoding(charset.name());
            res.setContentLength(bytes.length);
            res.getOutputStream().write(bytes);
            res.getOutputStream().flush();
        }
    }
}
