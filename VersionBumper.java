
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionBumper {
    private static final String TEMP_FILE_NAME = "build.gradle";
    private Matcher matcher;
    private Pattern pattern;
    private Path originalPath;
    private String flavor = "";
    private Pattern versionNamePattern = Pattern.compile("\\d\\.\\d\\.\\d");
    ;
    private Pattern versionCodePattern = Pattern.compile("(\\d)+");
    private Path TEMPORARY_FILE;

    public static void main(String[] args) {
      /*  String input = "       nfl_det{";
//        String patt = "nfl_det(\\s)*\\{";
        String patt = "nfl_det(\\s)*\\{";
        Pattern versionNamePattern = Pattern.compile("(\\d)+");
        pattern = Pattern.compile(patt);
        matcher = pattern.matcher(input);
        while (matcher.find()) {
            System.out.printf("found text:%s at start:%d, end:%d", matcher.group(), matcher.start(), matcher.end());
        }*/

        if(args.length<2) {
            System.out.println("Usage: java VersionBumper {flavor} {path}!");
            System.exit(-1);
        }

        VersionBumper b = new VersionBumper(args[0],args[1]);


//        VersionBumper b = new VersionBumper("nba_phx", "/Users/kedarparanjape/projects/nbamobile/NBAMobile/build.gradle");
        VersionSet current = b.parse();
        VersionSet newVersion = current.increment();
        System.out.println("The current version is " + current);
        System.out.println("The new versions will be:" + newVersion + "\nContinue? [y/n]");
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in))) {
            String choice = bufferedReader.readLine();
            if (choice != null) {
                if (choice.trim().toLowerCase().equals("y")) {
                    if(b.setVersion(current, newVersion)) {
                        addToGit();
                    }
//                    System.out.println("Status:" + ret);
                } else {
                    System.out.println("No changes have been made");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*VersionSet vs = new VersionSet();
        vs.versionCode = "16121099";
        vs.versionName = "10.9.9";
        System.out.println("new vs:" + vs.increment());*/

        /*Path outt = Paths.get(System.getenv("HOME"),"blah");
        try(BufferedWriter bufferedWriter = Files.newBufferedWriter(outt,StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            bufferedWriter.append("Wowza streaming server bwahahah");
        } catch (IOException e) {
            e.printStackTrace();
        }*/

    }

    private static void addToGit() {
        System.out.println("Adding to VCS");
        try {
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("git add .").waitFor(1,TimeUnit.SECONDS);
            Process out= runtime.exec(new String[]{"git","commit","-m","Bump version for release"});
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(out.getInputStream()))) {
                String line;
                while((line=reader.readLine())!=null) {
                    System.out.println(line);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private VersionBumper(String flavor, String strPath) {
        this.flavor = flavor;
        originalPath = Paths.get(strPath);
        pattern = Pattern.compile(flavor + "(\\s)*\\{");
        matcher = pattern.matcher("");
        TEMPORARY_FILE = Paths.get(System.getenv("HOME"), TEMP_FILE_NAME);
    }

    static class VersionSet {
        private String versionName = "";
        private String versionCode = "";

        /**
         * Returns a new versionset by incrementing the current by one
         * Rules: If the versionName is represented as [major.minor.patch], then minor and patch range from 0-9. No limit
         * for major
         *
         * @return
         */
        VersionSet increment() {
            VersionSet newV = new VersionSet();
            String[] versionList = versionName.split("\\.");
            if (versionList.length == 3) {
                int major = Integer.parseInt(versionList[0]);
                int minor = Integer.parseInt(versionList[1]);
                int patch = Integer.parseInt(versionList[2]);
                if (major <= 0 || minor < 0 || minor > 9 || patch < 0 || patch > 9) {
                    System.err.println("Unknown versioning scheme already in use. Aborting");
                    System.exit(-4);
                }
                int noDigitsInThisVersionName = 3 + (major >= 10 ? 1 : 0);

                if (patch < 9) {
                    patch++;
                } else if (minor < 9) {
                    minor++;
                    patch = 0;
                } else {
                    major++;
                    minor = patch = 0;
                }

                String newVersionName = String.join(".", String.valueOf(major), String.valueOf(minor), String.valueOf(patch));
                String newVersionCode = this.versionCode.substring(0, this.versionCode.length() - noDigitsInThisVersionName) + newVersionName.replaceAll("\\.", "");
                newV.versionName = newVersionName;
                newV.versionCode = newVersionCode;
            }
            return newV;
        }

        @Override
        public String toString() {
            return "versionName:" + versionName + ", versionCode:" + versionCode;
        }
    }

    private VersionSet parse() {
        VersionSet current = new VersionSet();
        boolean stageOne = false, stageTwo = false, stageThree = false;
        try (BufferedReader reader = Files.newBufferedReader(originalPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                matcher.reset();
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    stageOne = true;
                }
                if (stageOne && line.contains("versionName")) {
                    //found our line
                    Matcher matcher = versionNamePattern.matcher(line);
                    if (matcher.find()) {
                        current.versionName = matcher.group();
                    }
                    stageTwo = true;
                }
                if (stageOne && line.contains("versionCode")) {
                    Matcher matcher = versionCodePattern.matcher(line);
                    if (matcher.find()) {
                        current.versionCode = matcher.group();
                    }
                    stageThree = true;
                }
                if (stageOne && stageTwo && stageThree) {
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException ex) {
            System.err.println("Version code non integral. This utility will not function");
            System.exit(-2);
        }
        return current;
    }

    private boolean setVersion(VersionSet oldVer, VersionSet newVer) {
        boolean stageOne = false, stageTwo = false, stageThree = false;
        try (BufferedReader reader = Files.newBufferedReader(originalPath);
             PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(TEMPORARY_FILE, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            String line;
            while ((line = reader.readLine()) != null) {
                matcher.reset();
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    stageOne = true;
                }
                if (stageOne && line.contains("versionName")) {
                    //found our line
                    line = line.replace(oldVer.versionName, newVer.versionName);
                    stageTwo = true;
                }
                if (stageOne && line.contains("versionCode")) {
                    line = line.replace(oldVer.versionCode, newVer.versionCode);
                    stageThree = true;
                }
                if (stageOne && stageTwo && stageThree) {
                    stageOne = stageTwo = stageThree = false;   //this prevents any other matching lines from being changed
                }
                printWriter.println(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (NumberFormatException ex) {
            System.err.println("Version code non integral. This utility will not function");
            System.exit(-2);
            return false;
        }
        System.out.println("Temporary file successfully created");
        return overwriteOriginalFile();

    }

    private boolean overwriteOriginalFile() {
        try {
             Files.copy(TEMPORARY_FILE, originalPath,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error replacing original file with temporary file. Aborting");
            System.exit(-5);
            e.printStackTrace();
            return false;
        }
        return true;
    }


}
