package br.tec.ebz.connid.connector.lg.processing;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;

import java.util.*;

public class ObjectProcessing {

    public static <T> T getAttributeValue(String name, Class<T> type, Set<Attribute> attributes) {
        Attribute attr = AttributeUtil.find(name, attributes);

        if (attr == null) {
            return null;
        }

        if (String.class.equals(type)) {
            return (T) AttributeUtil.getStringValue(attr);
        } else if (Long.class.equals(type)) {
            return (T) AttributeUtil.getLongValue(attr);
        } else if (Integer.class.equals(type)) {
            return (T) AttributeUtil.getIntegerValue(attr);
        } else if (GuardedString.class.equals(type)) {
            return (T) AttributeUtil.getGuardedStringValue(attr);
        } else if (Boolean.class.equals(type)) {
            return (T) AttributeUtil.getBooleanValue(attr);
        } else if (Date.class.equals(type)) {
            return (T) AttributeUtil.getDateValue(attr);
        } else {
            throw new InvalidAttributeValueException("Unknown value type " + type);
        }
    }

    public static <T> List<T> getAttributeListValue(String name, Set<Attribute> attributes) {
        Attribute attr = AttributeUtil.find(name, attributes);

        if (attr == null) {
            return Collections.emptyList();
        }

        Object value = attr.getValue();
        if (value == null){
            return Collections.emptyList();
        }

        return (List<T>) value;
    }

    protected AttributeInfo buildAttributeInfo(String name, Class type, String nativeName, AttributeInfo.Flags... flags) {
        AttributeInfoBuilder aib = new AttributeInfoBuilder(name);
        aib.setType(type);
        if (nativeName == null) {
            nativeName = name;
        }
        aib.setNativeName(nativeName);
        if (flags.length != 0) {
            Set<AttributeInfo.Flags> set = new HashSet<>(Arrays.asList(flags));
            aib.setFlags(set);
        }
        return aib.build();
    }

    protected void addAttribute(ConnectorObjectBuilder builder, String attributeName, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            if (value instanceof Collection) {
                builder.addAttribute(attributeName, (Collection<?>) value);
            } else {
                builder.addAttribute(attributeName, value);
            }
        }
    }
}

