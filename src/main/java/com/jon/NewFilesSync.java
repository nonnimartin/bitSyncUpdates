import java.io.*; 
import java.util.Collection;
import org.apache.commons.io.*;
import org.apache.commons.io.filefilter.*;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.JSONObject;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.*;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;


public class NewFilesSync {

    public static class FileInfoMap{
        Map<String, String> filesMap;
    }

    public static class FileInfo{
        String md5;
        Long lastModified;
    }

    public static Map<String,String> readProperties() throws IOException{
        Properties prop = new Properties();
        InputStream input = null;
        input = new FileInputStream("config.properties");
        prop.load(input);

        Map<String, String> propertiesMap = new HashMap<String, String>();

        propertiesMap.put("fromEmail", prop.getProperty("fromEmail"));
        propertiesMap.put("fromPassword", prop.getProperty("fromPassword"));
        propertiesMap.put("toEmails", prop.getProperty("toEmails"));
        propertiesMap.put("toName", prop.getProperty("toName"));
        propertiesMap.put("fromName", prop.getProperty("fromName"));
        propertiesMap.put("storage", prop.getProperty("storage"));
        propertiesMap.put("syncLocation", prop.getProperty("syncLocation"));
        propertiesMap.put("thisFile", prop.getProperty("thisFile"));

        return propertiesMap;
    }

    public static Collection getSyncDirFiles(String dirPath){
        File dir = new File(dirPath);
        Collection files = FileUtils.listFiles(
          dir, 
          new RegexFileFilter("^(.*?)"), 
          DirectoryFileFilter.DIRECTORY
          );

        return files;
    }

    public static void printList(Collection list){
        for (Object thisFile : list){
            System.out.println(thisFile.toString());
        }
    }

    public static Long getLastModifiedFromPath(String filePath){
        File thisFile = new File(filePath);
        return thisFile.lastModified();
    }

    public static Map<String, Map<String,String>> deserializeToMap(String jsonInput) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, Map<String,String>>> typeRef = new TypeReference<HashMap<String, Map<String,String>>>() {};
        Map<String, Map<String,String>> map = mapper.readValue(jsonInput, typeRef);
        return map;
    }

    public static List<String> getRecentFilesList(String storageFile) throws IOException{
        List<String>  recentFilesList     = new ArrayList<String>();
        File thisStorageFile                          = new File(storageFile);
        Map<String,Map<String,String>> storedFileInfo;

        storedFileInfo = deserializeToMap(FileUtils.readFileToString(thisStorageFile));
        Set<String> filePathsSet                      = storedFileInfo.keySet();

        //get unix time 2 months ago
        long twoMonthsAgo = System.currentTimeMillis() - (60 * 24 * 60 * 60 * 1000L);
        System.out.println("two months ago = " + twoMonthsAgo);

        for (String thisKey : filePathsSet){
            if (Long.parseLong(storedFileInfo.get(thisKey).get("lastModified")) > twoMonthsAgo && !thisKey.contains(".DS_Store")){
                recentFilesList.add(thisKey);
            }
        }

        return recentFilesList;

    }

    public static void sendEmail(String filesJson) throws IOException{
        
        Map<String, String> propertiesMap = readProperties();

        List<String> recentFilesList = getRecentFilesList(propertiesMap.get("storage"));
        String recentFilesString     = new String();
        
        //add list of files modified in the last 2 months
        if (!recentFilesList.isEmpty()){
            recentFilesString += "\n Files added or changed in the last 2 months: \n \n";

            for (String thisFile : recentFilesList){
                recentFilesString += thisFile + "\n";
            }
        }

        String fromName = propertiesMap.get("fromName");
        String toName   = propertiesMap.get("toName");
        String fromEmail = propertiesMap.get("fromEmail");
        String fromPass  = propertiesMap.get("fromPassword");
        String toEmails  = propertiesMap.get("toEmails");

        List<String> toEmailsList = new ArrayList<String>(Arrays.asList(toEmails.split(",")));

        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {};
        Map<String, String> map = mapper.readValue(filesJson, typeRef);

        String newFilesString = new String();

        for (String thisKey : map.keySet()){
            String theseFilesString = map.get(thisKey);
            newFilesString += thisKey + ": " + theseFilesString + "\n";
        }

        newFilesString += recentFilesString;

        if (!newFilesString.isEmpty()){
            for (String thisEmail : toEmailsList){ 

                 Email email = EmailBuilder.startingBlank()
                .from(fromName, fromEmail + "@gmail.com")
                .to(toName, thisEmail)
                .withSubject("Updates on Juche Files")
                .withPlainText("Here are the latest changes in our BitTorrent Sync: " + "\n" + newFilesString)
                .buildEmail();

                Mailer thisMailer = MailerBuilder.withSMTPServer("smtp.gmail.com", 587, fromEmail, fromPass).buildMailer();

                thisMailer.sendMail(email);
            }
        }
    }

    public static String readFileToString(String filePath) throws FileNotFoundException, IOException{
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String everything = sb.toString();
            return everything;
        } finally {
            br.close();
        }
    }

    public static void writeToFile(String targetPath, String content) {

        final String FILENAME = targetPath;


        BufferedWriter bw = null;
        FileWriter fw = null;

        try {
            String thisContent = content;

            fw = new FileWriter(FILENAME);
            bw = new BufferedWriter(fw);
            bw.write(thisContent);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {

                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    public static void main(String[] args) throws FileNotFoundException, IOException{

        Map<String, String> propertiesMap = readProperties();
       
        File storageFile = new File(propertiesMap.get("storage"));
        Map<String, Map<String,String>> storedFileInfo;
        Set<String> filePathsSet;
        JSONObject emailsJSON = new JSONObject();
        
        if (storageFile.exists()){
            storedFileInfo = deserializeToMap(readFileToString(propertiesMap.get("storage")));
            filePathsSet                       = storedFileInfo.keySet();
        }else{
            storedFileInfo = new HashMap();
            filePathsSet = Collections.emptySet();
        }

        String thisLocationString       = propertiesMap.get("thisFile");
        Collection filesCollection      = getSyncDirFiles(propertiesMap.get("syncLocation"));
        Map<String, String> fileInfoMap = new HashMap<String, String>();
        JSONObject totalJSON            = new JSONObject();

        for (Object thisFile : filesCollection){
            FileInfo thisFileInfo = new FileInfo();
            String thisFilePath = thisFile.toString();

            try{

                if (!filePathsSet.contains(thisFilePath)) {

                    FileInputStream fis = new FileInputStream(new File(thisFilePath));
                    String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                    fis.close();

                    JSONObject thisJSON = new JSONObject();
                    thisJSON.put("md5", md5);
                    thisJSON.put("lastModified", getLastModifiedFromPath(thisFilePath));
                    
                    //update storage file
                    totalJSON.put(thisFilePath, thisJSON);

                    //add new files to newfiles in email map json
                    emailsJSON.put("newFiles", thisFilePath);

                }else {

                    Map<String, String> infoMap = storedFileInfo.get(thisFilePath);
                    FileInputStream fis = new FileInputStream(new File(thisFilePath));
                    String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                    fis.close();

                    JSONObject thisJSON = new JSONObject();
                    String storedMd5            = infoMap.get("md5");
                    String lastModified         = infoMap.get("lastModified");
                    thisJSON.put("md5", storedMd5);
                    thisJSON.put("lastModified", lastModified);

                    //update storage json
                    totalJSON.put(thisFilePath, thisJSON);

                    if (!storedMd5.equals(md5)){

                        fis = new FileInputStream(new File(thisFilePath));
                        md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                        fis.close();

                        thisJSON = new JSONObject();
                        thisJSON.put("md5", md5);
                        thisJSON.put("lastModified", getLastModifiedFromPath(thisFilePath));

                        //update storage json
                        totalJSON.put(thisFilePath, thisJSON);

                        //add changed file to changed files in email map json
                        emailsJSON.put("changedFiles", thisFilePath);
                    }   

                }

            }catch(IOException e){
                System.out.println("Caught exception: " + e.toString());
            }   
        }
        writeToFile(thisLocationString + "/storage.json", totalJSON.toString());
        writeToFile(thisLocationString + "/emails.json", emailsJSON.toString());
        String emailsFileString = readFileToString("emails.json");

        sendEmail(emailsFileString);

    }

}

