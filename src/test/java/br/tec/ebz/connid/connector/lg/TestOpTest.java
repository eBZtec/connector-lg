package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.framework.api.ConnectorFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TestOpTest extends BaseTestConnection {

    @Test
    public void shouldReturnApiHealth() {
        ConnectorFacade connection = setupConnector();
        connection.test();
    }

    @Test
    void test_shouldPass_whenJsonHasStatus() {
        ConnectorFacade connection = setupConnector();
        assertDoesNotThrow(connection::test);
    }
}
