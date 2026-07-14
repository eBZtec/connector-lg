package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.test.common.TestHelpers;

public abstract class BaseTestConnection {

    private final TestPropertiesReader propertiesReader;

    public BaseTestConnection() {
        propertiesReader = new TestPropertiesReader();
    }

    protected ConnectorFacade setupConnector(LGConfiguration config) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LGConnector.class, config);

        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(false);

        return factory.newInstance(impl);
    }

    protected ConnectorFacade setupConnector() {
        LGConfiguration configuration = new LGConfiguration();
        configuration.setUrlEndpoint(propertiesReader.getUrlEndpoint());
        configuration.setSenha(new GuardedString(propertiesReader.getSenha().toCharArray()));
        configuration.setUsuario(propertiesReader.setUsuario());
        configuration.setGuidtenant(propertiesReader.getGuidtenant());
        configuration.setAmbiente(propertiesReader.getAmbiente());

        return setupConnector(configuration);
    }
}
