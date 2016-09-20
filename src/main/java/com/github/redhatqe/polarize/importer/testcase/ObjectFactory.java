//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.09.20 at 10:42:54 AM EDT 
//


package com.github.redhatqe.polarize.importer.testcase;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.github.redhatqe.polarize.importer.testcase package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _AutomationScript_QNAME = new QName("", "automation-script");
    private final static QName _Description_QNAME = new QName("", "description");
    private final static QName _Setup_QNAME = new QName("", "setup");
    private final static QName _Assignee_QNAME = new QName("", "assignee");
    private final static QName _InitialEstimate_QNAME = new QName("", "initial-estimate");
    private final static QName _Title_QNAME = new QName("", "title");
    private final static QName _Teardown_QNAME = new QName("", "teardown");
    private final static QName _Tags_QNAME = new QName("", "tags");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.github.redhatqe.polarize.importer.testcase
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link TestStep }
     * 
     */
    public TestStep createTestStep() {
        return new TestStep();
    }

    /**
     * Create an instance of {@link TestStepColumn }
     * 
     */
    public TestStepColumn createTestStepColumn() {
        return new TestStepColumn();
    }

    /**
     * Create an instance of {@link Parameter }
     * 
     */
    public Parameter createParameter() {
        return new Parameter();
    }

    /**
     * Create an instance of {@link Functional }
     * 
     */
    public Functional createFunctional() {
        return new Functional();
    }

    /**
     * Create an instance of {@link Testcases }
     * 
     */
    public Testcases createTestcases() {
        return new Testcases();
    }

    /**
     * Create an instance of {@link ResponseProperties }
     * 
     */
    public ResponseProperties createResponseProperties() {
        return new ResponseProperties();
    }

    /**
     * Create an instance of {@link ResponseProperty }
     * 
     */
    public ResponseProperty createResponseProperty() {
        return new ResponseProperty();
    }

    /**
     * Create an instance of {@link Properties }
     * 
     */
    public Properties createProperties() {
        return new Properties();
    }

    /**
     * Create an instance of {@link Property }
     * 
     */
    public Property createProperty() {
        return new Property();
    }

    /**
     * Create an instance of {@link Testcase }
     * 
     */
    public Testcase createTestcase() {
        return new Testcase();
    }

    /**
     * Create an instance of {@link Nonfunctional }
     * 
     */
    public Nonfunctional createNonfunctional() {
        return new Nonfunctional();
    }

    /**
     * Create an instance of {@link Structural }
     * 
     */
    public Structural createStructural() {
        return new Structural();
    }

    /**
     * Create an instance of {@link TestSteps }
     * 
     */
    public TestSteps createTestSteps() {
        return new TestSteps();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "automation-script")
    public JAXBElement<String> createAutomationScript(String value) {
        return new JAXBElement<String>(_AutomationScript_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "description")
    public JAXBElement<String> createDescription(String value) {
        return new JAXBElement<String>(_Description_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "setup")
    public JAXBElement<String> createSetup(String value) {
        return new JAXBElement<String>(_Setup_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "assignee")
    public JAXBElement<String> createAssignee(String value) {
        return new JAXBElement<String>(_Assignee_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "initial-estimate")
    public JAXBElement<String> createInitialEstimate(String value) {
        return new JAXBElement<String>(_InitialEstimate_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "title")
    public JAXBElement<String> createTitle(String value) {
        return new JAXBElement<String>(_Title_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "teardown")
    public JAXBElement<String> createTeardown(String value) {
        return new JAXBElement<String>(_Teardown_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "tags")
    public JAXBElement<String> createTags(String value) {
        return new JAXBElement<String>(_Tags_QNAME, String.class, null, value);
    }

}
