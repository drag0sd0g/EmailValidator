package com.dragos.emailvalidator;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class EmailValidatorDriver {

    private static final String SEPARATOR = ",";

    private static final int OK_RESULT = 250;
    private static final int DOMAIN_SERVICE_READY = 220;
    private static final int SMTP_PORT = 25;
    private static final String CHARSET = "UTF-8";

    public EmailValidatorDriver(String dirLocation, String fileName) {

        Path path = Paths.get(dirLocation, fileName);
        List<String> emails = Collections.<String>emptyList();
        try (Reader source = Files.newBufferedReader(
                path, Charset.forName(CHARSET));
             BufferedReader reader = new BufferedReader(source)) {
            emails = reader.lines()
                    .findFirst()
                    .map(line -> Arrays.asList(line.split(SEPARATOR)))
                    .get();

        } catch (IOException e) {
            System.err.println(e.getStackTrace());
        }

        emails.stream().forEach(email -> System.out.println(email + " " + isEmailValid(email)));
    }

    private boolean isEmailStringValid(String email) {
        boolean isValid = false;
        try {
            InternetAddress internetAddress = new InternetAddress(email);
            internetAddress.validate();
            isValid = true;
        } catch (AddressException e) {
            System.err.println("You are in catch block -- Exception Occurred for: " + email);
        }
        return isValid;
    }

    private ArrayList getMX(String hostName) throws NamingException {
        Hashtable env = new Hashtable();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        DirContext ictx = null;
        Attributes attrs = null;
        NamingEnumeration en = null;
        ArrayList res = new ArrayList();
        try {
            ictx = new InitialDirContext(env);
            attrs = ictx.getAttributes(hostName, new String[]{"MX"});

            System.out.println(String.format("fetched MX attributes for hostname %s", attrs));

            Attribute attr = attrs.get("MX");
            if ((attr == null) || (attr.size() == 0)) {
                attrs = ictx.getAttributes(hostName, new String[]{"A"});
                attr = attrs.get("A");
                if (attr == null)
                    throw new NamingException
                            ("No match for name '" + hostName + "'");
            }

            en = attr.getAll();


            while (en.hasMore()) {
                String mailhost;
                String x = (String) en.next();
                String f[] = x.split(" ");
                if (f.length == 1)
                    mailhost = f[0];
                else if (f[1].endsWith("."))
                    mailhost = f[1].substring(0, (f[1].length() - 1));
                else
                    mailhost = f[1];
                res.add(mailhost);

            }
        } catch (NamingException ne) {
            System.err.println(ne);
        }
        return res;
    }

    private boolean isEmailValid(String address) {

        if(!isEmailStringValid(address)) return false;

        int pos = address.indexOf('@');

        String domain = address.substring(++pos);
        ArrayList mxList;
        try {
            System.out.println("getting MX for domain " + domain);
            mxList = getMX(domain);
        } catch (NamingException ex) {
            return false;
        }

        if (mxList.isEmpty()) return false;

        for (int mx = 0; mx < mxList.size(); mx++) {
            boolean valid = false;
            try (Socket skt = new Socket((String) mxList.get(mx), SMTP_PORT);
                 BufferedReader rdr = new BufferedReader
                         (new InputStreamReader(skt.getInputStream()));
                 BufferedWriter wtr = new BufferedWriter
                         (new OutputStreamWriter(skt.getOutputStream()))) {
                int res;

                res = read(rdr);
                if (res != DOMAIN_SERVICE_READY) throw new Exception("Invalid header");
                write(wtr, "EHLO rgagnon.com");

                res = read(rdr);
                if (res != OK_RESULT) throw new Exception("Not ESMTP");

                // validate the sender address
                write(wtr, "MAIL FROM: <tim@orbaker.com>");
                res = read(rdr);
                if (res != OK_RESULT) throw new Exception("Sender rejected");

                write(wtr, "RCPT TO: <" + address + ">");
                res = read(rdr);

                write(wtr, "RSET");
                read(rdr);
                write(wtr, "QUIT");
                read(rdr);
                if (res != OK_RESULT)
                    throw new Exception("Address is not valid!");

                valid = true;
            } catch (Exception ex) {
                // Do nothing but try next host
                ex.printStackTrace();
            } finally {
                if (valid) return true;
            }
        }
        return false;
    }

    private int read(BufferedReader in) throws IOException {
        String line;
        int res = 0;

        while ((line = in.readLine()) != null) {
            String pfx = line.substring(0, 3);
            try {
                res = Integer.parseInt(pfx);
            } catch (Exception ex) {
                res = -1;
            }
            if (line.charAt(3) != '-') break;
        }
        System.out.println("read "+res);
        return res;
    }

    private void write(BufferedWriter wr, String text)
            throws IOException {
        wr.write(text + "\r\n");
        wr.flush();
        System.out.println("wrote "+text);
    }


    public static void main(String[] args) {
        new EmailValidatorDriver(args[0], args[1]);
    }

}