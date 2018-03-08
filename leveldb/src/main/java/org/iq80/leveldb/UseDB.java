package org.iq80.leveldb;

import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.IOException;
import java.io.*;

/**
 * Created by liukun on 2018/3/8.
 */
public class UseDB {

    public static void main(String[] args) throws IOException {
        System.out.println("Hello world");
        Options options = new Options();
        options.createIfMissing(true);
        DBFactory dbFactory = new Iq80DBFactory();
        DB db = null;
        try {
            db = dbFactory.open(new File("test"),options);
            db.put("Tampa".getBytes(), "rocks".getBytes());
            String value = new String(db.get(("Tampa").getBytes()));
            System.out.println(value);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
    }

}
