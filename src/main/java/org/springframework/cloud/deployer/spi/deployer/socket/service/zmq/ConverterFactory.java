package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.converter.Converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

/**
 * Created by A542458 on 12/10/2016.
 */
public class ConverterFactory {

    private static Log logger = LogFactory.getLog(ConverterFactory.class);

    public static Converter<byte[], Object> getDefaultInboundConverter() {
        return source -> {
            Object result = null;
            try {
                ByteArrayInputStream in = new ByteArrayInputStream(source);
                ObjectInputStream is = new ObjectInputStream(in);
                result = is.readObject();
            } catch (ClassNotFoundException e) {
                logger.error("Could not deserialize object", e);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        };
    }

    public static Converter<Object, byte[]> getDefaultOutboundConverter() {
        return source -> {
            byte[] result = null;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
                out.writeObject(source);
                result = bos.toByteArray();
            } catch (IOException e) {
                logger.error("Could not serialize object", e);
            }
            return result;
        };
    }
}
