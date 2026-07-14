package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.*;

import java.util.List;

public class FilterHandler implements FilterVisitor<String, String> {
    private static final String ALL = " ALL ";

    private final ObjectClass objectClass;

    public FilterHandler(ObjectClass objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public String visitAndFilter(String s, AndFilter andFilter) {
        return "";
    }

    @Override
    public String visitContainsFilter(String s, ContainsFilter containsFilter) {
        return "";
    }

    @Override
    public String visitContainsAllValuesFilter(String s, ContainsAllValuesFilter containsAllValuesFilter) {
        return "";
    }

    public String visitEqualsFilter(String s, EqualsFilter equalsFilter) {
        s = (s != null) ? s : "";

        Attribute attr = equalsFilter.getAttribute();
        if (attr == null) return null;

        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            List<Object> value = attr.getValue();
            String singleValue = (value != null && !value.isEmpty())
                    ? AttributeUtil.getStringValue(attr)
                    : ALL;
            return s + singleValue;
        }

        if (objectClass.is(ObjectClass.GROUP_NAME)) {
            List<Object> value = attr.getValue();
            String singleValue = (value != null && !value.isEmpty())
                    ? AttributeUtil.getStringValue(attr)
                    : ALL;
            return s + singleValue;
        }
        throw new UnsupportedOperationException("ObjectClass " + objectClass.getDisplayNameKey() + " not supported.");
    }

    @Override
    public String visitExtendedFilter(String s, Filter filter) {
        return "";
    }

    @Override
    public String visitGreaterThanFilter(String s, GreaterThanFilter greaterThanFilter) {
        return "";
    }

    @Override
    public String visitGreaterThanOrEqualFilter(String s, GreaterThanOrEqualFilter greaterThanOrEqualFilter) {
        return "";
    }

    @Override
    public String visitLessThanFilter(String s, LessThanFilter lessThanFilter) {
        return "";
    }

    @Override
    public String visitLessThanOrEqualFilter(String s, LessThanOrEqualFilter lessThanOrEqualFilter) {
        return "";
    }

    @Override
    public String visitNotFilter(String s, NotFilter notFilter) {
        return "";
    }

    @Override
    public String visitOrFilter(String s, OrFilter orFilter) {
        return "";
    }

    @Override
    public String visitStartsWithFilter(String s, StartsWithFilter startsWithFilter) {
        return "";
    }

    @Override
    public String visitEndsWithFilter(String s, EndsWithFilter endsWithFilter) {
        return "";
    }

    @Override
    public String visitEqualsIgnoreCaseFilter(String s, EqualsIgnoreCaseFilter equalsIgnoreCaseFilter) {
        return "";
    }
}
