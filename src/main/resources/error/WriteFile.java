import java.io.File;

class Main {

    public static void main(String[] args) {
        File file = new File("/test");
        if (!file.exists())
            file.mkdir();
    }
}
