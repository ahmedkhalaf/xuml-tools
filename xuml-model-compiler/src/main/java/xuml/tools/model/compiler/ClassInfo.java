package xuml.tools.model.compiler;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.HashMultimap.create;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.w3c.dom.Node;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import xuml.tools.miuml.metamodel.extensions.jaxb.Documentation;
import xuml.tools.miuml.metamodel.extensions.jaxb.Find;
import xuml.tools.miuml.metamodel.extensions.jaxb.Generation;
import xuml.tools.miuml.metamodel.extensions.jaxb.Marshaller;
import xuml.tools.miuml.metamodel.jaxb.ActivePerspective;
import xuml.tools.miuml.metamodel.jaxb.Association;
import xuml.tools.miuml.metamodel.jaxb.AssociativeReference;
import xuml.tools.miuml.metamodel.jaxb.AsymmetricPerspective;
import xuml.tools.miuml.metamodel.jaxb.AtomicType;
import xuml.tools.miuml.metamodel.jaxb.Attribute;
import xuml.tools.miuml.metamodel.jaxb.BinaryAssociation;
import xuml.tools.miuml.metamodel.jaxb.BooleanType;
import xuml.tools.miuml.metamodel.jaxb.Class;
import xuml.tools.miuml.metamodel.jaxb.CreationEvent;
import xuml.tools.miuml.metamodel.jaxb.EnumeratedType;
import xuml.tools.miuml.metamodel.jaxb.Event;
import xuml.tools.miuml.metamodel.jaxb.Extension;
import xuml.tools.miuml.metamodel.jaxb.Generalization;
import xuml.tools.miuml.metamodel.jaxb.IdentifierAttribute;
import xuml.tools.miuml.metamodel.jaxb.IndependentAttribute;
import xuml.tools.miuml.metamodel.jaxb.IntegerType;
import xuml.tools.miuml.metamodel.jaxb.Named;
import xuml.tools.miuml.metamodel.jaxb.NativeAttribute;
import xuml.tools.miuml.metamodel.jaxb.PassivePerspective;
import xuml.tools.miuml.metamodel.jaxb.Perspective;
import xuml.tools.miuml.metamodel.jaxb.RealType;
import xuml.tools.miuml.metamodel.jaxb.Reference;
import xuml.tools.miuml.metamodel.jaxb.ReferentialAttribute;
import xuml.tools.miuml.metamodel.jaxb.Relationship;
import xuml.tools.miuml.metamodel.jaxb.SpecializationReference;
import xuml.tools.miuml.metamodel.jaxb.State;
import xuml.tools.miuml.metamodel.jaxb.StateModelParameter;
import xuml.tools.miuml.metamodel.jaxb.StateModelSignature;
import xuml.tools.miuml.metamodel.jaxb.SymbolicType;
import xuml.tools.miuml.metamodel.jaxb.SymmetricPerspective;
import xuml.tools.miuml.metamodel.jaxb.Transition;
import xuml.tools.miuml.metamodel.jaxb.UnaryAssociation;
import xuml.tools.model.compiler.info.ClassExtensions;
import xuml.tools.model.compiler.info.Mult;
import xuml.tools.model.compiler.info.MyAttributeExtensions;
import xuml.tools.model.compiler.info.MyEvent;
import xuml.tools.model.compiler.info.MyFind;
import xuml.tools.model.compiler.info.MyIdAttribute;
import xuml.tools.model.compiler.info.MyIndependentAttribute;
import xuml.tools.model.compiler.info.MyJoinColumn;
import xuml.tools.model.compiler.info.MyJoinTable;
import xuml.tools.model.compiler.info.MyParameter;
import xuml.tools.model.compiler.info.MyReferenceMember;
import xuml.tools.model.compiler.info.MySpecializations;
import xuml.tools.model.compiler.info.MySubclassRole;
import xuml.tools.model.compiler.info.MyTransition;
import xuml.tools.model.compiler.info.MyType;
import xuml.tools.model.compiler.info.MyTypeDefinition;

/**
 * Provides information about a metmodel Class definition.
 * 
 * @author dxm
 * 
 */
public class ClassInfo {

    private final Class cls;
    private final String packageName;
    private final String schema;
    private final TypeRegister typeRegister = new TypeRegister();
    private final Lookups lookups;
    private final static Marshaller extensionsMarshaller = new Marshaller();
    private final NameManager nameManager;

    /**
     * Constructor.
     * 
     * @param nameManager
     * @param cls
     * @param packageName
     * @param classDescription
     * @param schema
     * @param lookups
     */
    public ClassInfo(NameManager nameManager, Class cls, String packageName, String schema,
            Lookups lookups) {
        this.nameManager = nameManager;
        this.cls = cls;
        this.packageName = packageName;
        this.schema = schema;
        this.lookups = lookups;
    }

    /**
     * The full package name
     * 
     * @return
     */
    String getPackage() {
        return packageName;
    }

    /**
     * Returns the class description.
     * 
     * @return
     */
    String getClassDescription() {
        ClassExtensions x = getClassExtensions();
        if (x.getDocumentationContent().isPresent())
            return x.getDocumentationContent().get();
        else
            return "";
    }

    /**
     * Returns the list of groups of unique column names for the class.
     * 
     * @return
     */
    List<List<String>> getUniqueConstraintColumnNames() {
        HashMultimap<BigInteger, String> map = getIdentifierAttributeNames();
        List<List<String>> list = newArrayList();
        for (BigInteger i : map.keySet()) {
            if (!i.equals(BigInteger.ONE)) {
                List<String> cols = newArrayList();
                for (String attribute : map.get(i))
                    cols.add(nameManager.toColumnName(cls.getName(), attribute));
                list.add(cols);
            }
        }
        return list;
    }

    /**
     * Returns the name of the class.
     * 
     * @return
     */
    public String getName() {
        return cls.getName();
    }

    /**
     * Returns the Attributes from this class involved in a each identifier as a
     * Map.
     * 
     * @return
     */
    private HashMultimap<BigInteger, String> getIdentifierAttributeNames() {
        HashMultimap<BigInteger, Attribute> map = getIdentifierAttributes();
        HashMultimap<BigInteger, String> m = create();
        for (BigInteger i : map.keySet()) {
            m.putAll(i, getNames(map.get(i)));
        }
        return m;
    }

    private static Function<Attribute, String> attributeName = new Function<Attribute, String>() {
        @Override
        public String apply(Attribute a) {
            return a.getName();
        }
    };

    private Set<String> getNames(Set<Attribute> attributes) {
        return newHashSet(transform(attributes, attributeName));
    }

    private MyAttributeExtensions getAttributeExtensions(Attribute a) {
        String documentationMimeType = null;
        String documentationContent = null;
        boolean generated = false;
        boolean optional = false;
        for (Extension ext : a.getExtension()) {
            for (Object any : ext.getAny()) {
                Object e = getJaxbElementValue(any);
                if (e instanceof Documentation) {
                    Documentation doco = (Documentation) e;
                    documentationMimeType = doco.getMimeType();
                    documentationContent = doco.getContent();
                } else if (e instanceof Generation) {
                    Generation g = (Generation) e;
                    generated = g.isGenerated();
                } else if (e instanceof Optional) {
                    xuml.tools.miuml.metamodel.extensions.jaxb.Optional o = (xuml.tools.miuml.metamodel.extensions.jaxb.Optional) e;
                    optional = o.isOptional();
                }
            }
        }
        return new MyAttributeExtensions(generated, documentationMimeType, documentationContent,
                optional);
    }

    public ClassExtensions getClassExtensions() {
        String documentationContent = null;
        String documentationMimeType = null;
        for (Extension ext : cls.getExtension()) {
            for (Object any : ext.getAny()) {
                Object e = getJaxbElementValue(any);
                if (e instanceof Documentation) {
                    Documentation doco = (Documentation) e;
                    documentationMimeType = doco.getMimeType();
                    documentationContent = doco.getContent();
                }
            }
        }
        return new ClassExtensions(Optional.fromNullable(documentationContent),
                Optional.fromNullable(documentationMimeType));
    }

    private Object getJaxbElementValue(Object any) {
        Object e;
        try {
            Object element = extensionsMarshaller.unmarshal((Node) any);
            e = ((JAXBElement<?>) element).getValue();
        } catch (JAXBException ex) {
            // extension will not be used because not recognized
            e = null;
        }
        return e;
    }

    private HashMultimap<BigInteger, Attribute> getIdentifierAttributes() {
        HashMultimap<BigInteger, Attribute> map = HashMultimap.create();
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            Attribute attribute = element.getValue();
            for (IdentifierAttribute id : attribute.getIdentifier()) {
                map.put(id.getNumber(), attribute);
            }
        }
        return map;
    }

    String getSchema() {
        return schema;
    }

    String getTable() {
        return nameManager.toTableName(schema, cls.getName());
    }

    String getJavaClassSimpleName() {
        return Util.toClassSimpleName(cls.getName());
    }

    List<MyIdAttribute> getPrimaryIdAttributeMembers() {
        Set<Attribute> list = getIdentifierAttributes().get(BigInteger.ONE);
        return getMyIdAttributes(list);
    }

    private List<MyIdAttribute> getMyIdAttributes(Set<Attribute> list) {
        List<MyIdAttribute> result = newArrayList();
        for (Attribute attribute : list) {
            MyIdAttribute id;
            if (attribute instanceof NativeAttribute) {
                NativeAttribute a = (NativeAttribute) attribute;
                id = createMyIdAttribute(a);
            } else {
                ReferentialAttribute a = (ReferentialAttribute) attribute;
                id = createMyIdAttribute(a);
            }
            result.add(id);
        }
        return result;
    }

    private MyIdAttribute createMyIdAttribute(ReferentialAttribute a) {
        Reference ref = a.getReference().getValue();
        Relationship rel = lookups.getRelationship(ref.getRelationship());
        String otherClassName = getOtherClassName(rel);
        return getPrimaryIdAttribute(a, ref, otherClassName);
    }

    private String getOtherClassName(Relationship rel) {
        String otherClassName;
        if (rel instanceof BinaryAssociation) {
            BinaryAssociation b = (BinaryAssociation) rel;
            otherClassName = getOtherClassName(b);
        } else if (rel instanceof UnaryAssociation) {
            // TODO
            throw new RuntimeException("not sure how to do this one yet");
        } else if (rel instanceof Generalization) {
            Generalization g = (Generalization) rel;
            otherClassName = getOtherClassName(g);
        } else
            throw new RuntimeException(
                    "this relationship type not implemented: " + rel.getClass().getName());
        return otherClassName;
    }

    private String getOtherClassName(Generalization g) {
        if (cls.getName().equals(g.getSuperclass()))
            throw new RuntimeException(
                    "cannot use an id from a specialization as primary id member: " + g.getRnum());
        else
            return g.getSuperclass();
    }

    private String getOtherClassName(BinaryAssociation b) {
        String otherClassName;
        if (isActiveSide(b))
            otherClassName = b.getPassivePerspective().getViewedClass();
        else
            otherClassName = b.getActivePerspective().getViewedClass();
        return otherClassName;
    }

    private MyIdAttribute getPrimaryIdAttribute(ReferentialAttribute a, Reference ref,
            String otherClassName) {
        MyIdAttribute p = getOtherPrimaryIdAttribute(a, ref, otherClassName);
        if (p != null)
            return new MyIdAttribute(a.getName(),
                    nameManager.toFieldName(cls.getName(), a.getName()),
                    nameManager.toColumnName(cls.getName(), a.getName()), otherClassName,
                    nameManager.toColumnName(otherClassName, p.getAttributeName()), p.getType(),
                    getAttributeExtensions(a));
        else
            throw new RuntimeException("attribute not found!");
    }

    private MyIdAttribute getOtherPrimaryIdAttribute(ReferentialAttribute a, Reference ref,
            String otherClassName) {
        ClassInfo otherInfo = getClassInfo(otherClassName);
        // look for attribute
        String otherAttributeName;
        if (ref.getAttribute() == null)
            otherAttributeName = a.getName();
        else
            otherAttributeName = ref.getAttribute();
        List<MyIdAttribute> members = otherInfo.getPrimaryIdAttributeMembers();
        for (MyIdAttribute p : members) {
            if (otherAttributeName.equals(p.getAttributeName())) {
                return p;
            }
        }
        // not found
        throw new RuntimeException(
                "could not find attribute <" + ref.getAttribute() + " in class " + otherClassName);

    }

    private ClassInfo getClassInfo(String otherClassName) {
        ClassInfo otherInfo = new ClassInfo(nameManager, lookups.getClassByName(otherClassName),
                packageName, schema, lookups);
        return otherInfo;
    }

    private boolean isActiveSide(BinaryAssociation b) {
        return b.getActivePerspective().getViewedClass().equals(cls.getName());
    }

    public boolean hasCompositeId() {
        return getIdentifierAttributes().get(BigInteger.ONE).size() > 1;
    }

    private String getFieldName(String attribute) {
        HashMultimap<BigInteger, Attribute> map = getIdentifierAttributes();
        Set<Attribute> idAttributes = map.get(BigInteger.ONE);
        if (idAttributes.size() > 1 || idAttributes.size() == 0)
            return Util.toJavaIdentifier(attribute);
        else {
            if (idAttributes.iterator().next().getName().equals(attribute))
                return "id";
            else
                return Util.toJavaIdentifier(attribute);
        }
    }

    private MyIdAttribute createMyIdAttribute(NativeAttribute a) {
        return new MyIdAttribute(a.getName(), getFieldName(a.getName()),
                Util.toColumnName(a.getName()), getTypeDefinition(a.getType()),
                getAttributeExtensions(a));
    }

    private MyIndependentAttribute createMyIndependentAttribute(NativeAttribute a) {

        boolean inIdentifier = false;
        for (Attribute attribute : getIdentifierAttributes().values()) {
            if (a.getName().equals(attribute.getName()))
                inIdentifier = true;
        }
        MyAttributeExtensions extensions = getAttributeExtensions(a);
        final boolean isNullable;
        if (inIdentifier)
            isNullable = false;
        else
            isNullable = extensions.isOptional();

        return new MyIndependentAttribute(a.getName(), getFieldName(a.getName()),
                nameManager.toColumnName(cls.getName(), a.getName()),
                getTypeDefinition(a.getType()), isNullable, "description", extensions);
    }

    List<MyIndependentAttribute> getNonPrimaryIdIndependentAttributeMembers() {
        List<MyIndependentAttribute> list = newArrayList();
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            if (element.getValue() instanceof IndependentAttribute) {
                IndependentAttribute a = (IndependentAttribute) element.getValue();
                if (!isMemberOfPrimaryIdentifier(a)) {
                    list.add(createMyIndependentAttribute(a));
                }
            }
        }
        return list;
    }

    private List<MyIndependentAttribute> getIndependentAttributeMembers() {
        List<MyIndependentAttribute> list = newArrayList();
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            if (element.getValue() instanceof IndependentAttribute) {
                IndependentAttribute a = (IndependentAttribute) element.getValue();
                list.add(createMyIndependentAttribute(a));
            }
        }
        return list;
    }

    private boolean isMemberOfPrimaryIdentifier(IndependentAttribute a) {
        for (IdentifierAttribute idAttribute : a.getIdentifier()) {
            if (idAttribute.getNumber().intValue() == 1) {
                return true;
            }
        }
        return false;
    }

    List<MyEvent> getEvents() {
        if (cls.getLifecycle() == null)
            return newArrayList();
        List<MyEvent> list = newArrayList();
        CreationEvent creationEvent = getCreationEvent();
        for (JAXBElement<? extends Event> element : cls.getLifecycle().getEvent()) {
            Event event = element.getValue();
            boolean isCreationEvent = event == creationEvent;
            MyEvent myEvent = getEvent(event, isCreationEvent);
            list.add(myEvent);
        }
        return list;
    }

    private MyEvent getEvent(Event event, boolean isCreationEvent) {
        final StateModelSignature signature;
        final String stateName;

        if (event.getEventSignature() != null) {
            signature = event.getEventSignature();
            stateName = null;
        } else {
            // TODO of eventSignature is null then get signature from
            // destination state
            State destinationState = getDestinationState(event);
            if (destinationState != null) {
                if (destinationState.getStateSignature() != null)
                    signature = destinationState.getStateSignature();
                else
                    signature = new StateModelSignature() {
                        @Override
                        public List<StateModelParameter> getStateModelParameter() {
                            return Collections.emptyList();
                        }
                    };
                stateName = destinationState.getName();
            } else {
                signature = null;
                stateName = null;
            }
        }

        if (signature == null)
            throw new RuntimeException("event/state signature not found for " + cls.getName()
                    + ",event=" + event.getName());

        List<MyParameter> parameters = getParameters(signature);

        MyEvent myEvent = new MyEvent(event.getName(), Util.toClassSimpleName(event.getName()),
                parameters, stateName, getStateSignatureInterfaceName(stateName), isCreationEvent);
        return myEvent;
    }

    private List<MyParameter> getParameters(final StateModelSignature signature) {
        List<MyParameter> parameters = Lists.newArrayList();
        for (StateModelParameter p : signature.getStateModelParameter()) {
            parameters.add(new MyParameter(Util.toJavaIdentifier(p.getName()),
                    lookups.getJavaType(p.getType())));
        }
        return parameters;
    }

    private State getDestinationState(Event event) {
        State destinationState = null;

        for (MyTransition transition : getTransitions()) {
            if (transition.getEventId().equals(event.getID().toString())) {
                for (State state : cls.getLifecycle().getState()) {
                    if (transition.getToState().equals(state.getName())) {
                        destinationState = state;
                    }
                }
            }
        }
        return destinationState;
    }

    private String getStateSignatureInterfaceName(final String stateName) {
        if (stateName == null)
            return null;
        else
            return "StateSignature_" + Util.upperFirst(Util.toJavaIdentifier(stateName));
    }

    Collection<String> getStateNames() {
        Set<String> set = new TreeSet<String>();
        if (cls.getLifecycle() == null)
            return newArrayList();
        else {
            for (State state : cls.getLifecycle().getState())
                set.add(state.getName());
            return set;
        }
    }

    List<MyTransition> getTransitions() {
        List<MyTransition> list = Lists.newArrayList();
        for (Transition transition : cls.getLifecycle().getTransition()) {
            // TODO what to do about event name? Event inheritance is involved.
            String eventName = getEventName(transition.getEventID());
            list.add(new MyTransition(eventName, Util.toClassSimpleName(eventName),
                    transition.getEventID().toString(), transition.getState(),
                    transition.getDestination()));

        }
        CreationEvent creation = getCreationEvent();
        if (creation != null) {
            String eventName = getEventName(creation.getID());
            list.add(new MyTransition(eventName, Util.toClassSimpleName(eventName),
                    creation.getID().toString(), null, creation.getState()));
        }
        return list;
    }

    private CreationEvent getCreationEvent() {
        for (JAXBElement<? extends Event> element : cls.getLifecycle().getEvent()) {
            if (element.getValue() instanceof CreationEvent)
                return (CreationEvent) element.getValue();
        }
        return null;
    }

    private String getEventName(BigInteger eventId) {
        for (JAXBElement<? extends Event> ev : cls.getLifecycle().getEvent()) {
            if (ev.getValue().getID().equals(eventId))
                return ev.getValue().getName();
        }
        return null;
    }

    String getStateAsJavaIdentifier(String stateName) {
        for (State state : cls.getLifecycle().getState())
            if (state.getName().equals(stateName))
                // TODO use nameManager
                return Util.toJavaConstantIdentifier(stateName);
        throw new RuntimeException("state not found: " + stateName);
    }

    boolean isSuperclass() {
        return lookups.isSuperclass(cls.getName());
    }

    boolean isSubclass() {
        return lookups.isSpecialization(cls.getName());
    }

    boolean isAssociationClass() {
        return lookups.associationForAssociationClass(cls.getName()).isPresent();
    }

    MySubclassRole getSubclassRole() {
        // TODO Auto-generated method stub
        return null;
    }

    List<MyReferenceMember> getReferenceMembers() {

        List<MyReferenceMember> list = Lists.newArrayList();
        List<Association> associations = lookups.getAssociations(cls);
        for (Association a : associations) {
            List<MyReferenceMember> m = createMyReferenceMembers(a, cls);
            list.addAll(m);
        }
        for (Generalization g : lookups.getGeneralizations()) {
            for (Named specialization : g.getSpecializedClass()) {
                if (g.getSuperclass().equals(cls.getName()))
                    list.add(createMyReferenceMember(g, specialization, cls, true));
                else if (specialization.getName().equals(cls.getName()))
                    list.add(createMyReferenceMember(g, specialization, cls, false));
            }
        }
        Optional<Association> ass = lookups.associationForAssociationClass(cls.getName());
        if (ass.isPresent()) {
            // current class is an Association Class so prepare the implicit
            // associations from the ends to the Association Class
            if (ass.get() instanceof BinaryAssociation) {
                addReferenceMembers(list, ass);
            }
            // TODO handle unary association classes
        }
        return list;
    }

    private void addReferenceMembers(List<MyReferenceMember> list, Optional<Association> ass) {
        {
            BinaryAssociation b = (BinaryAssociation) ass.get();
            BinaryAssociation b2 = new BinaryAssociation();
            ActivePerspective p1 = new ActivePerspective();
            p1.setViewedClass(cls.getName());
            p1.setOnePerspective(b.getActivePerspective().isOnePerspective());
            p1.setConditional(b.getPassivePerspective().isConditional());
            p1.setPhrase(b.getActivePerspective().getPhrase());
            b2.setActivePerspective(p1);
            PassivePerspective p2 = new PassivePerspective();
            p2.setViewedClass(b.getActivePerspective().getViewedClass());
            p2.setOnePerspective(true);
            p2.setConditional(false);
            p2.setPhrase(b.getPassivePerspective().getPhrase());
            b2.setPassivePerspective(p2);
            b2.setRnum(b.getRnum());
            list.addAll(createMyReferenceMembers(b2, cls));
        }

        {
            BinaryAssociation b = (BinaryAssociation) ass.get();
            BinaryAssociation b2 = new BinaryAssociation();
            ActivePerspective p1 = new ActivePerspective();
            p1.setViewedClass(cls.getName());
            p1.setOnePerspective(b.getActivePerspective().isOnePerspective());
            p1.setConditional(b.getActivePerspective().isConditional());
            p1.setPhrase(b.getPassivePerspective().getPhrase());
            b2.setActivePerspective(p1);
            PassivePerspective p2 = new PassivePerspective();
            p2.setViewedClass(b.getPassivePerspective().getViewedClass());
            p2.setOnePerspective(true);
            p2.setConditional(false);
            p2.setPhrase(b.getActivePerspective().getPhrase());
            b2.setPassivePerspective(p2);
            b2.setRnum(b.getRnum());
            list.addAll(createMyReferenceMembers(b2, cls));
        }
    }

    private MyReferenceMember createMyReferenceMember(Generalization g, Named spec, Class cls,
            boolean isSuperclass) {
        if (isSuperclass) {
            return createSuperclassReferenceMember(g, spec, cls);
        } else {
            return createNonSuperclassReferenceMember(g, cls);
        }
    }

    private MyReferenceMember createNonSuperclassReferenceMember(Generalization g, Class cls) {
        ClassInfo infoOther = getClassInfo(g.getSuperclass());
        String fieldName = nameManager.toFieldName(cls.getName(), g.getSuperclass(), g.getRnum());
        String thisFieldName = nameManager.toFieldName(g.getSuperclass(), cls.getName(),
                g.getRnum());
        List<MyJoinColumn> joins = newArrayList();
        for (MyIdAttribute member : infoOther.getPrimaryIdAttributeMembers()) {
            // TODO handle when matching attribute not found, use some
            // default, see schema
            MyJoinColumn jc = createJoinColumn(g, member);
            joins.add(jc);
        }
        return new MyReferenceMember(g.getSuperclass(), infoOther.getClassFullName(), Mult.ZERO_ONE,
                Mult.ONE, "generalizes", "specializes", fieldName, joins, thisFieldName,
                (MyJoinTable) null, false, g.getRnum().toString(), Collections.emptyList());
    }

    private MyJoinColumn createJoinColumn(Generalization g, MyIdAttribute member) {
        String attributeName = getMatchingAttributeName(g.getRnum(), member.getAttributeName());
        MyJoinColumn jc = new MyJoinColumn(
                nameManager.toColumnName(g.getSuperclass(), attributeName), member.getColumnName());
        return jc;
    }

    private MyReferenceMember createSuperclassReferenceMember(Generalization g, Named spec,
            Class cls) {
        ClassInfo infoOther = getClassInfo(spec.getName());
        String fieldName = nameManager.toFieldName(cls.getName(), spec.getName(), g.getRnum());
        String thisFieldName = nameManager.toFieldName(spec.getName(), cls.getName(), g.getRnum());
        return new MyReferenceMember(spec.getName(), infoOther.getClassFullName(), Mult.ONE,
                Mult.ZERO_ONE, "specializes", "generalizes", fieldName, null, thisFieldName,
                (MyJoinTable) null, false, g.getRnum().toString(), Collections.emptyList());
    }

    private List<MyReferenceMember> createMyReferenceMembers(Association a, Class cls) {
        if (a instanceof BinaryAssociation)
            return createMyReferenceMembers((BinaryAssociation) a, cls);
        else
            return createMyReferenceMember((UnaryAssociation) a, cls);
    }

    private List<MyReferenceMember> createMyReferenceMember(UnaryAssociation a, Class cls) {
        SymmetricPerspective p = a.getSymmetricPerspective();
        List<MyJoinColumn> joins = newArrayList();
        List<MyJoinColumn> joins2 = newArrayList();
        for (MyIdAttribute member : getPrimaryIdAttributeMembers()) {
            String attributeName = member.getAttributeName() + " R" + a.getRnum();

            joins.add(new MyJoinColumn(nameManager.toColumnName(cls.getName(), attributeName),
                    member.getColumnName()));
            MyJoinColumn jc = new MyJoinColumn(member.getColumnName(),
                    nameManager.toColumnName(cls.getName(), attributeName));
            joins2.add(jc);
        }
        List<MyReferenceMember> list = Lists.newArrayList();

        String fieldName1 = nameManager.toFieldName(cls.getName(), p.getPhrase() + " Inverse",
                a.getRnum());
        String fieldName2 = nameManager.toFieldName(cls.getName(), p.getPhrase(), a.getRnum());

        Mult fromMult;
        if (toMult(p).equals(Mult.MANY) || toMult(p).equals(Mult.ZERO_ONE))
            fromMult = Mult.ZERO_ONE;
        else
            fromMult = Mult.ONE;

        list.add(new MyReferenceMember(getJavaClassSimpleName(), getClassFullName(), fromMult,
                toMult(p), p.getPhrase() + " Inverse", p.getPhrase(), fieldName2, joins2,
                fieldName1, null, false, a.getRnum().toString(), Collections.emptyList()));
        list.add(new MyReferenceMember(getJavaClassSimpleName(), getClassFullName(), toMult(p),
                fromMult, p.getPhrase(), p.getPhrase() + " Inverse", fieldName1, joins, fieldName2,
                null, false, a.getRnum().toString(), Collections.emptyList()));

        if (a.getAssociationClass() != null) {
            // TODO get this working
            // MyReferenceMember ref =
            // createImplicitReferenceMemberToAssociationClass(
            // a, cls);
            // list.add(ref);
        }
        return list;
    }

    private MyReferenceMember createImplicitReferenceMemberToAssociationClass(UnaryAssociation a,
            Class cls) {
        BinaryAssociation a2 = new BinaryAssociation();

        ActivePerspective active = new ActivePerspective();
        active.setPhrase(a.getSymmetricPerspective().getPhrase());
        active.setOnePerspective(true);
        active.setConditional(false);
        active.setViewedClass(cls.getName());
        a2.setActivePerspective(active);

        PassivePerspective passive = new PassivePerspective();
        passive.setConditional(a.getSymmetricPerspective().isConditional());
        passive.setOnePerspective(a.getSymmetricPerspective().isOnePerspective());
        passive.setPhrase(a.getSymmetricPerspective().getPhrase() + " Inverse");
        passive.setViewedClass(a.getAssociationClass());
        a2.setPassivePerspective(passive);
        a2.setRnum(a.getRnum());
        MyReferenceMember ref = createMyReferenceMemberForDirectAssociation(a2, cls, active,
                passive);
        return ref;
    }

    private List<MyReferenceMember> createMyReferenceMembers(BinaryAssociation a, Class cls) {
        AsymmetricPerspective pThis;
        AsymmetricPerspective pThat;

        if (a.getActivePerspective().getViewedClass().equals(cls.getName())) {
            pThis = a.getActivePerspective();
            pThat = a.getPassivePerspective();
        } else {
            pThis = a.getPassivePerspective();
            pThat = a.getActivePerspective();
        }
        List<MyReferenceMember> list = Lists.newArrayList();
        list.add(createMyReferenceMemberForDirectAssociation(a, cls, pThis, pThat));
        if (a.getAssociationClass() != null) {
            {
                MyReferenceMember ref = createImplicitReferenceMemberToAssociationClass(a, cls,
                        pThis, pThat);
                list.add(ref);
            }
        }
        return list;
    }

    private MyReferenceMember createImplicitReferenceMemberToAssociationClass(BinaryAssociation a,
            Class cls, AsymmetricPerspective pThis, AsymmetricPerspective pThat) {
        BinaryAssociation a2 = new BinaryAssociation();
        ActivePerspective active = new ActivePerspective();
        active.setPhrase(pThis.getPhrase());
        active.setOnePerspective(true);
        active.setConditional(false);
        active.setViewedClass(cls.getName());
        a2.setActivePerspective(active);

        PassivePerspective passive = new PassivePerspective();
        passive.setConditional(pThat.isConditional());
        passive.setOnePerspective(false);
        passive.setPhrase(pThat.getPhrase());
        passive.setViewedClass(a.getAssociationClass());
        a2.setPassivePerspective(passive);
        a2.setRnum(a.getRnum());
        MyReferenceMember ref = createMyReferenceMemberForDirectAssociation(a2, cls, active,
                passive);
        return ref;
    }

    private MyReferenceMember createMyReferenceMemberForDirectAssociation(BinaryAssociation a,
            Class cls, AsymmetricPerspective pThis, AsymmetricPerspective pThat) {

        String otherClassName = pThat.getViewedClass();
        ClassInfo infoOther = getClassInfo(otherClassName);

        List<MyJoinColumn> joins;
        if (pThat.isOnePerspective())
            joins = getJoinColumns(a.getRnum(), cls, infoOther);
        else
            joins = Lists.newArrayList();

        final String fieldName = nameManager.toFieldName(cls.getName(), pThat.getViewedClass(),
                a.getRnum());

        List<OtherId> otherIds = infoOther.getPrimaryIdAttributeMembers().stream()
                .map(att -> new OtherId(att.getFieldName(), att.getType()))
                .collect(Collectors.toList());
        // now establish the name of the field for this class as seen in the
        // other class
        String mappedBy = nameManager.toFieldName(otherClassName, cls.getName(), a.getRnum());
        boolean inPrimaryId = inPrimaryId(a.getRnum());

        MyJoinTable manyToMany = createManyToMany(a, cls, infoOther, pThis, pThat);

        return new MyReferenceMember(otherClassName, infoOther.getClassFullName(), toMult(pThis),
                toMult(pThat), pThis.getPhrase(), pThat.getPhrase(), fieldName, joins, mappedBy,
                manyToMany, inPrimaryId, a.getRnum().toString(), otherIds);
    }

    public static class OtherId {

        private final String fieldName;
        private final MyTypeDefinition type;

        OtherId(String fieldName, MyTypeDefinition type) {
            this.fieldName = fieldName;
            this.type = type;
        }

        public String getFieldName() {
            return fieldName;
        }

        public MyTypeDefinition getType() {
            return type;
        }

        @Override
        public String toString() {
            return "OtherId [fieldName=" + fieldName + ", type=" + type + "]";
        }

    }

    private List<MyJoinColumn> getJoinColumns(BigInteger rnum, Class cls, ClassInfo infoOther) {
        List<MyJoinColumn> joins = Lists.newArrayList();
        for (MyIdAttribute member : infoOther.getPrimaryIdAttributeMembers()) {
            String attributeName = getMatchingAttributeName(rnum, member.getAttributeName());
            MyJoinColumn jc = new MyJoinColumn(
                    nameManager.toColumnName(cls.getName(), attributeName), member.getColumnName());
            joins.add(jc);
        }
        return joins;
    }

    private MyJoinTable createManyToMany(BinaryAssociation a, Class cls, ClassInfo infoOther,
            AsymmetricPerspective pThis, AsymmetricPerspective pThat) {
        if (!pThis.isOnePerspective() && !pThat.isOnePerspective()) {
            String joinClass;
            final List<MyJoinColumn> joinColumns = Lists.newArrayList();
            final List<MyJoinColumn> inverseJoinColumns = Lists.newArrayList();
            if (a.getAssociationClass() == null) {
                // TODO use NameManager to get implicit join class name, must do
                // this because there could be multiple many to many
                // associations between the two classes.
                joinClass = getImplicitJoinClass(pThis, pThat);
                for (MyIdAttribute member : getPrimaryIdAttributeMembers()) {
                    joinColumns.add(new MyJoinColumn(
                            nameManager.toColumnName(joinClass,
                                    cls.getName() + " " + member.getAttributeName()),
                            member.getColumnName()));
                }
                for (MyIdAttribute member : infoOther.getPrimaryIdAttributeMembers()) {
                    inverseJoinColumns.add(new MyJoinColumn(
                            nameManager.toColumnName(joinClass,
                                    infoOther.getName() + " " + member.getAttributeName()),
                            member.getColumnName()));
                }
            } else {
                joinClass = a.getAssociationClass();
                ClassInfo joinInfo = getClassInfo(joinClass);
                for (MyIdAttribute member : getPrimaryIdAttributeMembers()) {
                    String otherAttributeName = joinInfo
                            .getMatchingAttributeNameForAssociativeReference(a.getRnum(),
                                    cls.getName(), member.getAttributeName());
                    joinColumns.add(new MyJoinColumn(
                            nameManager.toColumnName(joinClass, otherAttributeName),
                            member.getColumnName()));
                }
                for (MyIdAttribute member : infoOther.getPrimaryIdAttributeMembers()) {
                    String otherAttributeName = joinInfo
                            .getMatchingAttributeNameForAssociativeReference(a.getRnum(),
                                    infoOther.getName(), member.getAttributeName());
                    inverseJoinColumns.add(new MyJoinColumn(
                            nameManager.toColumnName(joinClass, otherAttributeName),
                            member.getColumnName()));
                }
            }

            MyJoinTable mm = new MyJoinTable(nameManager.toTableName(getSchema(), joinClass),
                    getSchema(), joinColumns, inverseJoinColumns);
            return mm;
        } else
            return null;
    }

    private String getImplicitJoinClass(AsymmetricPerspective pThis, AsymmetricPerspective pThat) {
        String joinClass;
        if (pThis.getViewedClass().compareTo(pThat.getViewedClass()) < 0)
            joinClass = pThis.getViewedClass() + " " + pThat.getViewedClass();
        else
            joinClass = pThat.getViewedClass() + " " + pThis.getViewedClass();
        return joinClass;
    }

    private String getMatchingAttributeNameForAssociativeReference(BigInteger rNum,
            String otherClassName, String otherAttributeName) {
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            Attribute a = element.getValue();
            if (a instanceof ReferentialAttribute) {
                ReferentialAttribute r = (ReferentialAttribute) a;
                if (r.getReference().getValue() instanceof AssociativeReference) {
                    AssociativeReference ar = (AssociativeReference) r.getReference().getValue();
                    if (ar.getRelationship().equals(rNum)
                            && ar.getAttribute().equals(otherAttributeName)
                            && ar.getClazz().equals(otherClassName))
                        return r.getName();
                }
            }
        }
        throw new RuntimeException("could not find matching attribute " + cls.getName() + " R"
                + rNum + " " + otherClassName + "." + otherAttributeName);
    }

    private boolean inPrimaryId(BigInteger rnum) {
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            Attribute a = element.getValue();
            if (a instanceof ReferentialAttribute) {
                ReferentialAttribute r = (ReferentialAttribute) a;
                if (r.getReference().getValue().getRelationship().equals(rnum))
                    for (IdentifierAttribute ia : r.getIdentifier()) {
                        if (ia.getNumber().equals(BigInteger.ONE))
                            return true;
                    }
            }
        }
        return false;
    }

    private String getMatchingAttributeName(BigInteger rNum, String otherAttributeName) {
        for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
            Attribute a = element.getValue();
            if (a instanceof ReferentialAttribute) {
                ReferentialAttribute r = (ReferentialAttribute) a;
                if (r.getReference().getValue().getRelationship().equals(rNum)
                        && r.getReference().getValue().getAttribute().equals(otherAttributeName))
                    return r.getName();
            }
        }
        throw new RuntimeException("could not find matching attribute " + cls.getName() + " R"
                + rNum + " " + otherAttributeName);
    }

    private static Mult toMult(Perspective p) {
        if (p.isConditional() && p.isOnePerspective())
            return Mult.ZERO_ONE;
        else if (p.isConditional() && !p.isOnePerspective())
            return Mult.MANY;
        else if (p.isOnePerspective())
            return Mult.ONE;
        else
            return Mult.ONE_MANY;
    }

    Set<String> getAtLeastOneFieldChecks() {
        // TODO Auto-generated method stub
        return Sets.newHashSet();
    }

    String getImports(String relativeToClass) {
        return getTypes().getImports(relativeToClass);
    }

    String getIdColumnName() {
        // TODO Auto-generated method stub
        return "ID";
    }

    String getContextPackageName() {
        // TODO Auto-generated method stub
        return packageName;
    }

    TypeRegister getTypes() {
        return typeRegister;
    }

    Type getType(String name) {
        String javaClassName = lookups.getJavaType(name);
        return new Type(javaClassName);
    }

    public List<MySpecializations> getSpecializations() {
        List<MySpecializations> list = Lists.newArrayList();

        for (Generalization g : lookups.getGeneralizations()) {
            if (g.getSuperclass().equals(cls.getName())) {
                Set<String> fieldNames = Sets.newHashSet();
                for (Named spec : g.getSpecializedClass()) {
                    // get the attribute name
                    String attributeName = null;
                    for (JAXBElement<? extends Attribute> element : cls.getAttribute()) {
                        if (element.getValue() instanceof ReferentialAttribute) {
                            ReferentialAttribute r = (ReferentialAttribute) element.getValue();
                            Reference ref = r.getReference().getValue();
                            if (ref instanceof SpecializationReference) {
                                if (ref.getRelationship().equals(g.getRnum()))
                                    attributeName = r.getName();
                            }
                        }
                    }
                    if (attributeName == null)
                        throw new RuntimeException(
                                "could not find attribute name for generalization " + g.getRnum()
                                        + ", specialization " + spec.getName());
                    String fieldName = nameManager.toFieldName(cls.getName(), spec.getName(),
                            g.getRnum());
                    fieldNames.add(fieldName);
                }
                list.add(new MySpecializations(g.getRnum(), fieldNames));
            }
        }
        return list;
    }

    public List<MyFind> getFinders() {
        Map<String, MyIndependentAttribute> map = Maps.newHashMap();
        for (MyIndependentAttribute a : getIndependentAttributeMembers())
            map.put(a.getAttributeName(), a);

        List<MyFind> finds = Lists.newArrayList();
        for (Extension ext : cls.getExtension()) {
            for (Object any : ext.getAny()) {
                Object e = getJaxbElementValue(any);
                if (e != null && e instanceof Find) {
                    Find find = (Find) e;
                    List<MyIndependentAttribute> list = Lists.newArrayList();
                    for (xuml.tools.miuml.metamodel.extensions.jaxb.Attribute attribute : find
                            .getAttribute())
                        list.add(map.get(attribute.getName()));
                    finds.add(new MyFind(list));
                }
            }
        }
        return finds;
    }

    public MyTypeDefinition getTypeDefinition(String name) {
        AtomicType t = lookups.getAtomicType(name);
        if (t instanceof SymbolicType)
            return getTypeDefinition((SymbolicType) t);
        else if (t instanceof BooleanType)
            return getTypeDefinition((BooleanType) t);
        else if (t instanceof EnumeratedType)
            return getTypeDefinition((EnumeratedType) t);
        else if (t instanceof IntegerType)
            return getTypeDefinition((IntegerType) t);
        else if (t instanceof RealType)
            return getTypeDefinition((RealType) t);
        else
            throw new RuntimeException("unexpected");
    }

    private MyTypeDefinition getTypeDefinition(RealType t) {
        return new MyTypeDefinition(t.getName(), MyType.REAL, new Type(Double.class), t.getUnits(),
                t.getPrecision(), t.getLowerLimit(), t.getUpperLimit(), t.getDefaultValue() + "",
                null, null, null, null, null, null);
    }

    private MyTypeDefinition getTypeDefinition(IntegerType t) {
        MyType myType;
        Type type;

        if ("date".equals(t.getName())) {
            myType = MyType.DATE;
            type = new Type(Date.class);
        } else if ("timestamp".equals(t.getName())) {
            myType = MyType.TIMESTAMP;
            type = new Type(Date.class);
        } else {
            myType = MyType.INTEGER;
            type = new Type(Integer.class);
        }

        return new MyTypeDefinition(t.getName(), myType, type, t.getUnits(), null,
                toBigDecimal(t.getLowerLimit()), toBigDecimal(t.getUpperLimit()),
                toString(t.getDefaultValue()), null, null, null, null, null, null);
    }

    private static BigDecimal toBigDecimal(BigInteger n) {
        if (n == null)
            return null;
        else
            return new BigDecimal(n);
    }

    private static String toString(BigInteger n) {
        if (n == null)
            return null;
        else
            return n.toString();
    }

    private MyTypeDefinition getTypeDefinition(EnumeratedType t) {
        // TODO do something type safe using generated enum because we can
        return new MyTypeDefinition(t.getName(), MyType.STRING, new Type(String.class), null, null,
                null, null, t.getDefaultValue().toString(), null, BigInteger.ONE,
                BigInteger.valueOf(4096L), "", "", ".*");
    }

    private MyTypeDefinition getTypeDefinition(BooleanType t) {
        return new MyTypeDefinition(t.getName(), MyType.BOOLEAN, new Type(Boolean.class), null,
                null, null, null, ((Boolean) t.isDefaultValue()).toString(), null, null, null, null,
                null, null);
    }

    private MyTypeDefinition getTypeDefinition(SymbolicType t) {
        if (t.getName().equalsIgnoreCase("bytes")) {
            return new MyTypeDefinition(t.getName(), MyType.BYTES, new Type(byte.class, true), null,
                    null, null, null, t.getDefaultValue().toString(), null, t.getMinLength(),
                    t.getMaxLength(), t.getPrefix(), t.getSuffix(), t.getValidationPattern());
        } else
            return new MyTypeDefinition(t.getName(), MyType.STRING, new Type(String.class), null,
                    null, null, null, t.getDefaultValue().toString(), null, t.getMinLength(),
                    t.getMaxLength(), t.getPrefix(), t.getSuffix(), t.getValidationPattern());
    }

    final public String getBehaviourPackage() {
        return getPackage() + ".behaviour";
    }

    final public String getBehaviourFactoryFullClassName() {
        return getBehaviourPackage() + "." + getBehaviourFactorySimpleName();
    }

    final public String getBehaviourFullClassName() {
        return getBehaviourPackage() + "." + getJavaClassSimpleName() + "Behaviour";
    }

    final public String getBehaviourFactorySimpleName() {
        return getJavaClassSimpleName() + "BehaviourFactory";
    }

    final public String addType(java.lang.Class<?> cls) {
        return getTypes().addType(cls);
    }

    final public void addTypes(java.lang.Class<?>... classes) {
        getTypes().addTypes(classes);
    }

    final public String addType(String fullClassName) {
        return getTypes().addType(fullClassName);
    }

    final public String addType(Type type) {
        return getTypes().addType(type);
    }

    final public String getContextFullClassName() {
        return getContextPackageName() + ".Context";
    }

    final public String getBehaviourFactoryFieldName() {
        return Util.toJavaIdentifier(getBehaviourFactorySimpleName());
    }

    final public String getClassFullName() {

        return getPackage() + "." + getJavaClassSimpleName();
    }

    public String getEmbeddedIdSimpleClassName() {
        return getJavaClassSimpleName() + "Id";
    }

    public String getEmbeddedIdAttributeName() {
        return "id";
    }

    public boolean hasBehaviour() {
        return getEvents().size() > 0;
    }

    public boolean useGuiceInjection() {
        // TODO is this the best way to specify guice injection?
        return "true".equalsIgnoreCase(System.getProperty("guice"));
    }

}
