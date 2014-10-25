package com.dragos.emailvalidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public class EmailValidatorDriver {

    private static final String SEPARATOR = ",";

    public EmailValidatorDriver(String dirLocation, String fileName) {

        Path path = Paths.get(dirLocation, fileName);
        List<String> emails = Collections.<String>emptyList();
        try (Reader source = Files.newBufferedReader(
                path, Charset.forName("UTF-8"));
             BufferedReader reader = new BufferedReader(source)) {
            emails = reader.lines()
                    .findFirst()
                    .map(line -> Arrays.asList(line.split(SEPARATOR)))
                    .get();

        } catch (IOException e) {
            e.printStackTrace();
        }

        emails.stream().forEach(email -> System.out.println(email+" "+isEmailValid(email)));
    }

    private boolean isEmailValid(String email) {
        boolean isValid = false;
        try {
            InternetAddress internetAddress = new InternetAddress(email);
            internetAddress.validate();
            isValid = true;
        } catch (AddressException e) {
            System.out.println("You are in catch block -- Exception Occurred for: " + email);
        }
        return isValid;
    }

    public static void main(String[] args) {
        new EmailValidatorDriver(args[0], args[1]);
    }

}