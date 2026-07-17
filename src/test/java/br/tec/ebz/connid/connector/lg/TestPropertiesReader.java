package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.common.logging.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class TestPropertiesReader {

    private static final Log LOG = Log.getLog(TestPropertiesReader.class);

    private static final Properties PROPERTIES = new Properties();

    private static final String PROPERTIES_FILE = "/teste_dev.properties";

    private static final String URL_ENDPOINT = "url_endpoint";
    private static final String PASSWORD = "senha";
    private static final String USUARIO = "usuario";
    private static final String GUIDTENANT = "guidtenant";
    private static final String AMBIENTE = "ambiente";
    private static final String EMPRESA = "empresas";

    public TestPropertiesReader() {
        try {
            PROPERTIES.load(getClass().getResourceAsStream(PROPERTIES_FILE));
        } catch (FileNotFoundException e) {
            LOG.error(e, "File not found: {0}", e.getLocalizedMessage());
        } catch (IOException e) {
            LOG.error(e, "IO exception occurred {0}", e.getLocalizedMessage());
        } catch (NullPointerException e) {
            LOG.error(e, "Properties file not found", e.getLocalizedMessage());
        }
    }

    public String getUrlEndpoint() {
        return (String) PROPERTIES.get(URL_ENDPOINT);
    }

    public String getSenha() {
        return (String) PROPERTIES.get(PASSWORD);
    }

    public String setUsuario() {
        return (String) PROPERTIES.get(USUARIO);
    }

    public String getGuidtenant() {
        return (String) PROPERTIES.get(GUIDTENANT);
    }

    public String getAmbiente() {
        return (String) PROPERTIES.get(AMBIENTE);
    }

    public String getEmpresas() {
        return (String) PROPERTIES.get(EMPRESA);
    }

}
