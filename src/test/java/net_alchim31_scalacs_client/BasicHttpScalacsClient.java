package net_alchim31_scalacs_client;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * BasicHttpStsClient is a client used to send request to a scalacs running server.
 * The implementation is in java (instead of scala) to allow tool integrator to reuse the code (copy/paste or translate/adapte), (and the first 2 tools are in java).
 * An other example of client is scalacs.sh at the root of the project.
 *
 * @author davidB
 */
public abstract class BasicHttpScalacsClient {

    /**
     * request to createOrUpdate one or more project define in the Yaml syntax, each project definition should be separated by "---"
     * @return the output (log) of the request
     */
    public String sendRequestCreateOrUpdate(String yamlDef) throws Exception {
        String back = "";
        try {
            back = sendRequest("createOrUpdate", yamlDef);
        } catch (java.net.ConnectException exc) {
            startNewServer();
            back = sendRequest("createOrUpdate", yamlDef);
        }
        return back;
    }

    /**
     *
     * @return the output (log) of the request
     * @throws Exception
     */
    public String sendRequestCompile() throws Exception {
        return sendRequest("compile", null);
    }

    /**
     *
     * @return the output (log) of the request
     * @throws Exception
     */
    public String sendRequestClean() throws Exception {
        return sendRequest("clean", null);
    }

    /**
     *
     * @return the output (log) of the request
     * @throws Exception
     */
    public String sendRequestStop() throws Exception {
        return sendRequest("stop", null);
    }

    protected String sendRequest(String action, String data) throws Exception {
        URL url = new URL("http://127.0.0.1:27616/" + action);
        URLConnection cnx = url.openConnection();
        cnx.setDoOutput(StringUtils.isNotEmpty(data));
        cnx.setDoInput(true);
        if (StringUtils.isNotEmpty(data)) {
            OutputStream os = cnx.getOutputStream();
            try {
                IOUtils.copy(new StringReader(data), os);
            } finally {
                IOUtils.closeQuietly(os);
            }
        }
        InputStream is = cnx.getInputStream();
        try {
            String back = IOUtils.toString(is);
            return back;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * Implementation should provide a way to startNewServer (used if call sendRequestAdd and no server is up)
     *
     * @throws Exception
     */
    abstract public void startNewServer() throws Exception;
}
