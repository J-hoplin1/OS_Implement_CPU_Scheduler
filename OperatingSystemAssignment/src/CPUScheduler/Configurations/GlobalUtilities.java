package CPUScheduler.Configurations;

public class GlobalUtilities {
    // Exit Program
    public static void ExitProgram(){
        System.exit(0);
    }

    // Root Directory
    public static String getRootDirectory(){
        String RootDirectory = System.getProperty("user.dir");
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("windows")){
            RootDirectory += "\\CPUScheduler\\";
        }else if(os.contains("mac") || os.contains("nix") || os.contains("linux")){
            RootDirectory +="/CPUScheduler/";
        }
        return RootDirectory;
    }

    public static void BreakConsole(int millisecond){
        try{
            Thread.sleep(millisecond);
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}
