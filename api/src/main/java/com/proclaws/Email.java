package com.proclaws;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.json.JSONObject;

import static com.proclaws.Utils.CONFIG_FILE_PATH;
import static com.proclaws.Utils.safeCall;

public class Email {
    private static final JSONObject SMTP_CONFIG = safeCall(() -> new JSONObject(Files.readString(Paths.get(CONFIG_FILE_PATH))).getJSONObject("smtp"));

    private static final String FROM_ADDRESS = safeCall(() -> SMTP_CONFIG.getString("from"));
    
    private static final int SMTP_PORT = safeCall(() -> SMTP_CONFIG.getInt("smtpPort"));
    private static final String SMTP_SERVER = safeCall(() -> SMTP_CONFIG.getString("smtpServer"));
    private static final String SMTP_PASSWORD = safeCall(() -> SMTP_CONFIG.getString("smtpPassword"));


    public static void send(String to, String subject, String body, byte[] attachment) throws MessagingException {
        Session session = createTlsSession();
        MimeMessage email = new MimeMessage(session);
        // set headers
        email.addHeader("Content-type", "text/HTML; charset=UTF-8");
        email.addHeader("format", "flowed");
        email.addHeader("Content-Transfer-Encoding", "8bit");

        // set subject and body
        email.setFrom(FROM_ADDRESS);
        email.setSentDate(new Date());
        email.setText(body, "UTF-8");
        email.setSubject(subject, "UTF-8");
        email.addRecipients(javax.mail.Message.RecipientType.TO, InternetAddress.parse(to, false));

        // send email
        System.out.println("sending email to " + to);
        Transport.send(email);
        System.out.println("email sent successfully to " + to);
    }

    private static Session createTlsSession() {
        System.out.println("creating TLS session " + SMTP_SERVER + ":" + SMTP_PORT + " from " + FROM_ADDRESS);
        Properties props = new Properties();
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", true);
        props.put("mail.smtp.starttls.required", true);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.host", SMTP_SERVER);

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_ADDRESS, SMTP_PASSWORD);
            }
        });
    }
}
