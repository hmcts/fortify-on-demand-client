package uk.gov.hmcts.fortifyclient;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FortifyClient {

    private static final int ROOT_FOLDER_LOOKUP_MAX_DEPTH = 3;

    private final FortifyClientConfig configuration;

    public FortifyClient(FortifyClientConfig configuration) {
        this.configuration = configuration;
    }

    public ScanReport requestScanAndGetResults() throws Exception {

        exportFortifyJar();
        String zipFileName = buildZipFile();
        String[] fortifyScanArgs = buildScanArgs(zipFileName);

        log.info("About to request a Fortify scan for the zipped content of the repository...");
        Process proc = Runtime.getRuntime().exec(fortifyScanArgs);
        log.info("Fortify scan has been requested. Awaiting results...");
        proc.waitFor();
        InputStream in = proc.getInputStream();
        InputStream err = proc.getErrorStream();

        String consoleReport = IOUtils.toString(in, Charset.defaultCharset());

        log.info("Fortify scan call has been completed with exit code:" + proc.exitValue());
        log.info("Console report: " + consoleReport);

        if (proc.exitValue() != 0) {
            String errorReport = IOUtils.toString(err, Charset.defaultCharset());
            throw new FortifyClientException(errorReport);
        }

        return ScanReport.fromConsoleReport(consoleReport);
    }

    private String buildZipFile() throws Exception {
        String osName = System.getProperty("os.name").toLowerCase();

        log.debug(CommandRunner.run(osName.startsWith("windows") ? "cmd.exe /c dir" : "ls -lt"));

        File currentDir = new File(".");
        File rootDirectory = findRootDirectory(currentDir, ROOT_FOLDER_LOOKUP_MAX_DEPTH);
        if (rootDirectory == null) {
            rootDirectory = currentDir;
        }
        log.debug("Root directory has been selected : " + rootDirectory);
        String zipFileName = new FolderZipper().zip(rootDirectory, configuration.getExcludePatterns());
        log.info("Folder zipped into file : " + zipFileName);
        log.debug(CommandRunner.run(osName.startsWith("windows") ? "cmd.exe /c dir" : "ls -lt"));

        return zipFileName;

    }


    private File findRootDirectory(final File findFrom, int depth) {
        if (depth < 0) {
            return null;
        }
        
        File githubFolder = new File(findFrom.toString() + File.separator + ".github");
        File gitFolder = new File(findFrom.toString() + File.separator + ".git");

        if (gitFolder.exists() || githubFolder.exists()) {
            return findFrom;
        } else {
            return findRootDirectory(findFrom.getParentFile(), depth - 1);
        }
    }

    private String[] buildScanArgs(String zipFileName) {
        return new String[] { "java", "-jar", configuration.getRequired("fortify.jar.path"),
                "-portalurl", configuration.getRequired("fortify.portal.url"),
                "-apiurl",
                configuration.getRequired("fortify.api.url"), "-userCredentials",
                configuration
                        .getUsername(),
                configuration.getPassword(), "-tenantCode", configuration.getRequired(
                        "fortify.tenant.code"),
                "-zipLocation", "./" + zipFileName, "-releaseId", configuration
                        .getReleaseId(),
                "-entitlementPreferenceType", configuration.getRequired("fortify.entitlementpreferencetype"),
                "-inProgressScanActionType", configuration.getRequired("fortify.inprogressscanactiontype"),
                "-pollingInterval", configuration.getRequired("fortify.pollinginterval") };
    }

    private synchronized void exportFortifyJar() throws Exception {
        String fortifyScanJar = configuration.getRequired("fortify.jar.path");
        if (!Files.exists(Paths.get(fortifyScanJar))) {
            exportResource(fortifyScanJar);
        }
    }

    private void exportResource(String resourceName) throws Exception {
        InputStream stream = FortifyClient.class.getResourceAsStream("/" + resourceName);
        if (stream == null) {
            throw new Exception("Cannot get resource \"" + resourceName + "\" from Jar file.");
        }

        File targetFile = new File(resourceName);
        FileUtils.copyInputStreamToFile(stream, targetFile);
    }

}
