package servlets;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.Part;
import key_handler.PrivateKeySigner;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import utils.ProjectConstants;
//Optional 


@WebServlet(name="SignDocument", urlPatterns = {"/SignDocument"})
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024, // 1MB
    maxFileSize = 1024 * 1024 * 10, // 10MB
    maxRequestSize = 1024 * 1024 * 10 + 1024 // 10.1 MB 
)
public class SignDocument extends HttpServlet {

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        request.setCharacterEncoding("UTF-8");
        
        if (!ServletFileUpload.isMultipartContent(request)){
            response.setStatus(400);
            return;
        }
        
        try {            
            String name = request.getParameter("name");
            String lastname = request.getParameter("lastname");
            String identifier_str = request.getParameter("identifier");
            String age_str = request.getParameter("age");
            
            
            Person person = ValidateData.createPerson(name, lastname, identifier_str, age_str);
            if(person == null){
                response.setStatus(400);
                return;
            }
            
            String passphrase = request.getParameter("passphrase");
            Part filePart = request.getPart("file");
            //String file_name = filePart.getSubmittedFileName().replaceFirst("\\.[a-zA-Z]+$", "");
            String path = ProjectConstants.PDF_FILE_DEFAULT_PATH
                .concat(String.valueOf(System.currentTimeMillis()))
                .concat("-")
                .concat(ProjectConstants.PDF_FILE_DEFAULT_NAME);
            
            request.setAttribute("file_path", path);
            request.setAttribute("file_name", ProjectConstants.PDF_FILE_DEFAULT_NAME);
                        
            String private_key = Streams.asString(filePart.getInputStream(), "UTF-8");            
            File pdf = PDF.createPDF(person, path);
            
            PrivateKeySigner signer = new PrivateKeySigner();
            
            if("null".equals(String.valueOf(passphrase)))
                signer.loadPrivateKey(private_key, null);
            else
                signer.loadPrivateKey(private_key, passphrase);
            
            System.out.println(
                "Signature: " + Base64.getEncoder().encodeToString(signer.sign(pdf))
            );
            signer.sign(pdf);
            
            request.getRequestDispatcher("DownloadFile").forward(request, response);            
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.err.println(Arrays.toString(ex.getStackTrace()));
            response.setStatus(500);
        }
        
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }    
    
    private static class ValidateData{
        public static Person createPerson(String name, String lastname, String identifier_str, String age_str){
            Person person = null;
            Pattern pattern = Pattern.compile("^([a-zA-ZñÑáéíóúüÁÉÍÓÚÜ]{2,15}( )?){1,2}$");
            Matcher matcher = pattern.matcher(name);
            if(!matcher.matches())
                return null;
            matcher = pattern.matcher(lastname);
            if(!matcher.matches())
                return null;
            try {
                long identifier = Long.parseLong(identifier_str);
                byte age = Byte.parseByte(age_str);
                
                person = new Person(name, lastname, identifier, age);
            } catch(NumberFormatException e) {
                System.err.println(e.getMessage());
                System.err.println(Arrays.toString(e.getStackTrace()));
            }
            
            return person;
        }
    }
    
    private static class Person {
    
        private final String name;
        private final String lastname;
        private final long identifier;
        private final byte age;

        public Person(String name, String lastname, long identifier, byte age) {
            this.name = name;
            this.lastname = lastname;
            this.identifier = identifier;
            this.age = age;
        }

        public String getCompleteName() {
            return name + ' ' + lastname;
        }

        public long getIdentifier() {
            return identifier;
        }

        public byte getAge() {
            return age;
        }

    }
    
    private static class PDF {
        public static File createPDF(Person person, String path) {
            try {
                Document document = new Document();
                
                File file = new File(path);
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();
                
                Font font_center = FontFactory.getFont(FontFactory.TIMES_ROMAN, 16, Element.ALIGN_CENTER, BaseColor.BLACK);
                Font font_left = FontFactory.getFont(FontFactory.TIMES_ROMAN, 16, Element.ALIGN_LEFT, BaseColor.BLACK);
                Chunk line_separator = new Chunk(new LineSeparator(font_left));
                document.add(new Chunk(person.getCompleteName(), font_center));
                document.add(new Phrase("\n"));
                document.add(line_separator);
                document.add(Chunk.NEWLINE);
                document.add(new Chunk("\nBoleta: ".concat(String.valueOf(person.getIdentifier())), font_left));
                document.add(Chunk.NEWLINE);
                document.add(new Chunk("\nEdad: ".concat(String.valueOf(person.getAge())), font_left));
                document.add(Chunk.NEWLINE);
                
                document.close();
                
                return file;
                
            } catch(DocumentException | FileNotFoundException e) {
                System.err.println(e.getMessage());
                System.err.println(Arrays.toString(e.getStackTrace()));
            }
            return null;
        }
        
    }

}