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

        Attribute attribute = AttributeBuilder.build(Uid.NAME, "12214;1"); // demitido
//        Attribute attribute = AttributeBuilder.build(Uid.NAME, "31619;3"); // ativo
        EqualsFilter filter = new EqualsFilter(attribute);

        facade.search(ObjectClass.ACCOUNT, filter, handler, null);
        List<ConnectorObject> objects = handler.getObjects();
        AssertJUnit.assertEquals(1, objects.size());
    }

    @Test()
    public void shouldReturnUserByUidAndDefinePrimary() {
        ConnectorFacade facade = setupConnector();
        ListResultHandler handler = new ListResultHandler();

        /*
        # usuario com duas contas e uma das duas preenchida com demissao
        Abdias Atila Paulino Goncalves Pereira
        18456950700
        12214 : com DataDaRescisao | demitido
        31619 : sem DataDaRescisao | ativo
        */
//        Attribute attribute = AttributeBuilder.build(Uid.NAME, "12214;1");
//        Attribute attribute = AttributeBuilder.build(Uid.NAME, "31619;3");

        /*
        # usuaria com duas contas e as duas preenchidas com demissao
        Renata Lucia Cavalcanti Cunha
        02363034732
        10002 : com DataDaRescisao                  | nao primaria
        10019: com DataDaRescisao mais recente      | primaria
        */
//        Attribute attribute = AttributeBuilder.build(Uid.NAME, "10002;1");
        Attribute attribute = AttributeBuilder.build(Uid.NAME, "10019;1");
        EqualsFilter filter = new EqualsFilter(attribute);

        facade.search(ObjectClass.ACCOUNT, filter, handler, null);
        List<ConnectorObject> objects = handler.getObjects();
        AssertJUnit.assertEquals(1, objects.size());
    }
}
