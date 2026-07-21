package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

public class SearchUsersOpTest extends BaseTestConnection {

    @Test()
    public void shouldReturnAllUsers() {
        ConnectorFacade facade = setupConnector();
        ListResultHandler handler = new ListResultHandler();

        facade.search(ObjectClass.ACCOUNT, null, handler, null);
        List<ConnectorObject> objects = handler.getObjects();
        AssertJUnit.assertTrue("Size: " + objects.size(), objects.size() > 3372); // qtd total 3373
    }

    @Test()
    public void shouldReturnUserByUid() {
        ConnectorFacade facade = setupConnector();
        ListResultHandler handler = new ListResultHandler();

        Attribute attribute = AttributeBuilder.build(Uid.NAME, "62596"); // user pag 1
        EqualsFilter filter = new EqualsFilter(attribute);

        facade.search(ObjectClass.ACCOUNT, filter, handler, null);
        List<ConnectorObject> objects = handler.getObjects();
        AssertJUnit.assertEquals(1, objects.size());
    }
}
