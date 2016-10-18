//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.10.17 at 03:04:56 PM EDT 
//


package com.github.redhatqe.polarize.configuration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for importerType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="importerType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="testrun" type="{}testrunType" minOccurs="0"/>
 *         &lt;element name="endpoint" type="{}endpointType"/>
 *         &lt;element name="file" type="{}fileType" minOccurs="0"/>
 *         &lt;element name="selector" type="{}selectorType"/>
 *         &lt;element name="title" type="{}titleType" minOccurs="0"/>
 *         &lt;element name="test-suite" type="{}test-suiteType" minOccurs="0"/>
 *         &lt;element name="custom-fields" type="{}custom-fieldsType" minOccurs="0"/>
 *         &lt;element name="timeout" type="{}timeoutType"/>
 *       &lt;/sequence>
 *       &lt;attribute name="type" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "importerType", propOrder = {
    "testrun",
    "endpoint",
    "file",
    "selector",
    "title",
    "testSuite",
    "customFields",
    "timeout"
})
public class ImporterType {

    protected TestrunType testrun;
    @XmlElement(required = true)
    protected EndpointType endpoint;
    protected FileType file;
    @XmlElement(required = true)
    protected SelectorType selector;
    protected TitleType title;
    @XmlElement(name = "test-suite")
    protected TestSuiteType testSuite;
    @XmlElement(name = "custom-fields")
    protected CustomFieldsType customFields;
    @XmlElement(required = true)
    protected TimeoutType timeout;
    @XmlAttribute(name = "type")
    protected String type;

    /**
     * Gets the value of the testrun property.
     * 
     * @return
     *     possible object is
     *     {@link TestrunType }
     *     
     */
    public TestrunType getTestrun() {
        return testrun;
    }

    /**
     * Sets the value of the testrun property.
     * 
     * @param value
     *     allowed object is
     *     {@link TestrunType }
     *     
     */
    public void setTestrun(TestrunType value) {
        this.testrun = value;
    }

    /**
     * Gets the value of the endpoint property.
     * 
     * @return
     *     possible object is
     *     {@link EndpointType }
     *     
     */
    public EndpointType getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the value of the endpoint property.
     * 
     * @param value
     *     allowed object is
     *     {@link EndpointType }
     *     
     */
    public void setEndpoint(EndpointType value) {
        this.endpoint = value;
    }

    /**
     * Gets the value of the file property.
     * 
     * @return
     *     possible object is
     *     {@link FileType }
     *     
     */
    public FileType getFile() {
        return file;
    }

    /**
     * Sets the value of the file property.
     * 
     * @param value
     *     allowed object is
     *     {@link FileType }
     *     
     */
    public void setFile(FileType value) {
        this.file = value;
    }

    /**
     * Gets the value of the selector property.
     * 
     * @return
     *     possible object is
     *     {@link SelectorType }
     *     
     */
    public SelectorType getSelector() {
        return selector;
    }

    /**
     * Sets the value of the selector property.
     * 
     * @param value
     *     allowed object is
     *     {@link SelectorType }
     *     
     */
    public void setSelector(SelectorType value) {
        this.selector = value;
    }

    /**
     * Gets the value of the title property.
     * 
     * @return
     *     possible object is
     *     {@link TitleType }
     *     
     */
    public TitleType getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *     allowed object is
     *     {@link TitleType }
     *     
     */
    public void setTitle(TitleType value) {
        this.title = value;
    }

    /**
     * Gets the value of the testSuite property.
     * 
     * @return
     *     possible object is
     *     {@link TestSuiteType }
     *     
     */
    public TestSuiteType getTestSuite() {
        return testSuite;
    }

    /**
     * Sets the value of the testSuite property.
     * 
     * @param value
     *     allowed object is
     *     {@link TestSuiteType }
     *     
     */
    public void setTestSuite(TestSuiteType value) {
        this.testSuite = value;
    }

    /**
     * Gets the value of the customFields property.
     * 
     * @return
     *     possible object is
     *     {@link CustomFieldsType }
     *     
     */
    public CustomFieldsType getCustomFields() {
        return customFields;
    }

    /**
     * Sets the value of the customFields property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomFieldsType }
     *     
     */
    public void setCustomFields(CustomFieldsType value) {
        this.customFields = value;
    }

    /**
     * Gets the value of the timeout property.
     * 
     * @return
     *     possible object is
     *     {@link TimeoutType }
     *     
     */
    public TimeoutType getTimeout() {
        return timeout;
    }

    /**
     * Sets the value of the timeout property.
     * 
     * @param value
     *     allowed object is
     *     {@link TimeoutType }
     *     
     */
    public void setTimeout(TimeoutType value) {
        this.timeout = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

}