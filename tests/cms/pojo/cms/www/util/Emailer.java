package cms.www.util;

import java.net.*;
import java.util.Properties;
import java.util.Vector;
import javax.mail.*;
import javax.mail.internet.*;

import cms.www.AccessController;

/**
 * @author jfg32
 */
public class Emailer {
	
	// to test this, tunnel via SHH from localhost:25 via honeycrisp to smtp.csuglab.cornell.edu:25, and change below to "localhost".
    private String host = "smtp.csuglab.cornell.edu";
    // FIXME Add usable username/password
    private String user = "";
    private String password = "";
    private String fromaddress = "cms-devnull@csuglab.cornell.edu";
    public static final String DEBUG_EMAIL = "cms-developers-l@cs.cornell.edu";
    private Vector to = new Vector();
    private String subject = "";
    private String messageBody = "";
    private String replyTo;
    private Session mailSession = null;
    private String recipient = null;
    
    public Emailer() throws ConnectException {
        try {
            Properties props = new Properties();
            props.setProperty("mail.transport.protocol", "smtp");
            props.setProperty("mail.host", host);
            props.setProperty("mail.user", user);
            //props.setProperty("mail.password", password);
            mailSession = Session.getDefaultInstance(props, null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectException("Failed to create a connection to the email server");
        }
    }
    
    public void setFrom(String address) {
        this.fromaddress = address;
    }
    
    /**
     * Adds a new email address to include in this email's To field.
     * @param to
     */
    public void addTo(String to) {
        this.to.add(to);
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    public void setReplyTo(String replyTo) {
    	this.replyTo = replyTo;
    }
    
    public String getMessage() {
        return messageBody;
    }
    
    public void setMessage(String messageBody) {
        this.messageBody = messageBody;
    }
    
    public boolean sendEmail() {
    	return sendEmail(null);
    }
    
    public boolean sendEmail(String toAddr)
    {
    	//if(AccessController.debug) return true; //TODO shouldn't this replace the "e-mail dora" bit below?
        try {
            Transport transport = mailSession.getTransport();
            MimeMessage message = new MimeMessage(mailSession);
            
            if (AccessController.debug) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(DEBUG_EMAIL));
            } else {
	            if (toAddr != null) {
	            	
	            	message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddr));
	            } else {
		            for (int i=0; i < to.size(); i++) {
		                message.addRecipient(Message.RecipientType.BCC, new InternetAddress((String) to.get(i)));
		            }
	            }
            }

            if (replyTo != null && !replyTo.equals("")) {
            	Address[] addrArray = new Address[1];
                addrArray[0] = new InternetAddress(replyTo);
                message.setReplyTo(addrArray);
            }
            
            Address[] recipients = message.getAllRecipients();
            if (recipients == null || recipients.length == 0) return true;  // no receivers
            message.setText(messageBody);
            message.setSubject(subject);
            message.setFrom(new InternetAddress(fromaddress));
            transport.connect();
            transport.sendMessage(message, recipients);
            transport.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    public static String appendEmailFooter(String message, String courseCode) {
        String result = new String(message);        
        result += "\n\n\n";
        result += "-----------------------------------\n";
        result += "This message was auto-generated by Cornell CMS.  Do not reply to this message directly.  ";
        result += "To disable these emails, log into your CMS account, visit the " + courseCode + " homepage, and navigate to Notifications. ";
        return result;
    }
    
    /**
     * @return Returns the recipient.
     */
    public String getRecipient() {
        return recipient;
    }
    /**
     * @return Returns the subject.
     */
    public String getSubject() {
        return subject;
    }
}
