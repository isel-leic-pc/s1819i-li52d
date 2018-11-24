package pt.isel.pc.examples.nio;

import org.junit.Test;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class BufferExampleTests {

    @Test
    public void first() {
        Buffer buffer = ByteBuffer.allocate(7);
        assertEquals(0, buffer.position());
        assertEquals(7, buffer.capacity());
        assertEquals(7, buffer.limit());
        assertEquals(7, buffer.remaining());

        buffer.limit(5);
        assertEquals(7, buffer.capacity());
        assertEquals(5, buffer.limit());
    }

    @Test
    public void flipping() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        for(int i = 0 ; i<5; ++i) {
            buffer.put((byte)i);
        }
        assertEquals(5, buffer.position());
        assertEquals(8, buffer.limit());
        buffer.flip();
        assertEquals(0, buffer.position());
        assertEquals(5, buffer.limit());

    }
}
