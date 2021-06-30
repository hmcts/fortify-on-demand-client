package uk.gov.hmcts.fortifyscanner;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.Charset;

public class CommandRunner {

    public static String run(String command) throws IOException, InterruptedException {
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(command);
        pr.waitFor();

        return IOUtils.toString(pr.getInputStream(), Charset.defaultCharset());
    }
}
