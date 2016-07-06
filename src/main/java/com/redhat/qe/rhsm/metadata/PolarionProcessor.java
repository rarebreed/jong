package com.redhat.qe.rhsm.metadata;

import com.redhat.qe.rhsm.schema.*;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by stoner on 5/16/16.
 */
public class PolarionProcessor extends AbstractProcessor {
    private Types types;
    private Elements elements;
    private Messager msgr;
    private Filer filer;
    private Logger logger;
    String reqPath;
    String tcPath;

    /**
     * Contains the fully qualified name of a @Polarion decorated method
     */
    private class Meta<T> {
        public String packName;
        public String className;
        public String methName;
        public String qualifiedName;
        public T annotation;
    }

    /**
     * Recursive function that will get the fully qualified name of a method.
     *
     * Eg packageName.class.methodName
     * @param elem
     * @param accum
     * @return
     */
    String getTopLevel(Element elem, String accum, Meta m) {
        String tmp = elem.getSimpleName().toString();
        switch(elem.getKind()) {
            case PACKAGE:
                m.packName = elements.getPackageOf(elem).toString();
                tmp = m.packName;
                break;
            case CLASS:
                m.className = tmp;
                break;
            case METHOD:
                m.methName = tmp;
                break;
        }

        if (!Objects.equals(accum, "")) {
            accum = tmp + "." + accum;
        }
        else {
            accum = tmp;
        }

        if (elem.getKind() == ElementKind.PACKAGE)
            return accum;
        else {
            Element enclosing = elem.getEnclosingElement();
            return this.getTopLevel(enclosing, accum, m);
        }
    }

    private <T> List<Meta<T>> makeMeta(List<? extends Element> elements,
                                       Class<? extends Annotation> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Meta m = new Meta<T>();
            String full = this.getTopLevel(e, "", m);
            System.out.println(String.format("Fully qualified name is %s", full));
            m.qualifiedName = full;
            m.annotation = e.getAnnotation(ann);
            metas.add(m);
        }
        return metas;
    }


    /**
     * The PolarionProcessor actually needs to look for three annotation types:
     * - @Polarion: to get TestCase WorkItem information
     * - @Requirement: to get Requirement WorkItem information
     * - @Test: to get the existing description
     *
     * @param set
     * @param roundEnvironment
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        System.out.println("In process() method");

        // Get all the @Requirement top-level annotations
        // We will use the information here to generate the XML for requirements
        List<? extends Element> reqAnns = this.getRequirementAnnotations(roundEnvironment);
        List<Meta<Requirement>> requirements = this.makeMeta(reqAnns, Requirement.class);
        Map<String, Meta<Requirement>> methToRequirement = this.methodToMeta(requirements);

        // Get all the @Polarion annotations
        // Make a list of Meta types that store the fully qualified name of every @Polarion annotated
        // method.  We will use this to create a map of qualified_name => Polarion Annotation
        List<? extends Element> polAnns = this.getPolarionAnnotations(roundEnvironment);
        List<Meta<Polarion>> metas = this.makeMeta(polAnns, Polarion.class);
        Map<String, Meta<Polarion>> methToPolarion = this.methodToMeta(metas);
        methToPolarion.forEach((qual, ann) -> System.out.println(String.format("%s -> %s", qual, ann.toString())));

        // Get all the @Test annotations in order to get the description
        Map<String, String> methNameToDescription = this.getTestAnnotations(roundEnvironment);

        // We now have the mapping from qualified name to annotation.  So, process each TestCase object
        List<TestcaseType> tests = methToPolarion.entrySet().stream()
                .map(es -> {
                    String qualifiedName = es.getKey();
                    @Nonnull String desc = methNameToDescription.get(qualifiedName);
                    Meta<Polarion> meta = es.getValue();
                    return this.processTestCase(meta, desc);
                })
                .collect(Collectors.toList());



        // Convert the TestcaseType objects to XML
        // TODO: figure out how to get the project-id
        TestCaseMetadata tcmd = new TestCaseMetadata();
        tcmd.setProjectId(ProjectVals.RED_HAT_ENTERPRISE_LINUX_7);
        tcmd.setDryRun(true);

        TestCaseMetadata.Workitems wis = new TestCaseMetadata.Workitems();
        List<TestcaseType> tcs = wis.getTestcase();
        tcs.addAll(tests);
        tcmd.setWorkitems(wis);

        try {
            JAXBContext jaxbc = JAXBContext.newInstance(TestCaseMetadata.class);
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(tcmd, new File("/tmp/testing.xml"));
            marshaller.marshal(tcmd, System.out);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        return true;
    }


    private Map<String, ? extends Annotation> methodToAnnotation(List<? extends Element> elements,
                                                                 Class<? extends Annotation> ann) {
        return elements.stream()
                .collect(Collectors.toMap(e -> e.getSimpleName().toString(),
                                          e -> e.getAnnotation(ann)));

    }

    /**
     * Given a sequence of Meta objects, return a Map of the qualified name to the Annotation data
     *
     * @param elements
     * @param <T>
     * @return
     */
    private <T> Map<String, T> methodToAnnotation2(List<Meta<T>> elements) {
        Map<String, T> mToA = new HashMap<>();
        for(Meta<T> m: elements) {
            mToA.put(m.qualifiedName, m.annotation);
        }
        return mToA;
    }

    /**
     * Creates a map of fully qualified names to a Meta type
     *
     * @param metas
     * @param <T>
     * @return
     */
    private <T> Map<String, Meta<T>> methodToMeta(List<Meta<T>> metas) {
        return metas.stream()
                .collect(Collectors.toMap(m -> m.qualifiedName,
                        m -> m));
    }

    private List<? extends Element> getRequirementAnnotations(RoundEnvironment env) {
        String iface = Requirement.class.getSimpleName();
        return env.getElementsAnnotatedWith(Requirement.class)
                .stream()
                .map(ae -> {
                    if (ae.getKind() != ElementKind.METHOD ||
                            ae.getKind() != ElementKind.CLASS) {
                        this.errorMsg(ae, "Can only annotate classes or methods with @%s", iface);
                        return null;
                    }
                    return ae;
                })
                .filter(ae -> ae != null)
                .collect(Collectors.toList());
    }


    /**
     * FIXME: Pass in as argument the Annotation type and a lambda to pass to map
     * @param env
     * @return
     */
    private List<? extends Element> getPolarionAnnotations(RoundEnvironment env) {
        String iface = Polarion.class.getSimpleName();
        List<? extends Element> polAnns = env.getElementsAnnotatedWith(Polarion.class)
                .stream()
                .map(ae -> {
                    if (ae.getKind() != ElementKind.METHOD) {
                        this.errorMsg(ae, "Can only annotate methods with @%s", iface);
                    }
                    return ae;
                })
                .collect(Collectors.toList());
        return polAnns;
    }


    /**
     * Finds methods annotated with @Test and gets the description field.  It returns a map of
     * fully qualified name -> description
     *
     * @param env
     * @return
     */
    private Map<String, String> getTestAnnotations(RoundEnvironment env) {
        List<? extends Element> testAnns = env.getElementsAnnotatedWith(Test.class)
                .stream().collect(Collectors.toList());

        List<Meta<Test>> tests = this.makeMeta(testAnns, Test.class);

        Map<String, String> nameToDesc = new HashMap<>();
        for(Meta<Test> mt: tests) {
            String key = mt.qualifiedName;
            String val = mt.annotation.description();
            nameToDesc.put(key, val);
        }
        return nameToDesc;
    }


    public File getXMLFileForMethod(Element elem) {
        return null;
    }

    /**
     * Examines a Requirement object to obtain its values and generates an XML file
     *
     * First, it will check to see if id is an empty string.  Next, it will check if the xmlDesc value is also an
     * empty string.  If both are empty, then given the rest of the information from the annotation, it will generate
     * an XML file and place it in:
     *
     * resources/requirements/{package}/{class}/{methodName}.xml
     *
     * @param req
     */
    private void processRequirement(Requirement req) {

    }

    /**
     * Takes a feature file in gherkin style, and generates an XML file
     * @param featureFile
     */
    private void featureToRequirement(String featureFile) {

    }

    /**
     * Examples a Polarion object to obtain its values and generates an XML file if needed.
     * @param meta
     */
    private TestcaseType processTestCase(Meta<Polarion> meta, String description) {
        Polarion pol = meta.annotation;
        TestcaseType tc = new TestcaseType();
        tc.setAuthor(pol.author());
        tc.setDescription(description);
        tc.setTitle(meta.qualifiedName);

        // For automation, let's always assume we're in draft state
        TestcaseType.Status status = new TestcaseType.Status();
        status.setValue("draft");
        tc.setStatus(status);

        TestcaseType.Caseautomation ca = new TestcaseType.Caseautomation();
        ca.setValue(AutomationTypes.AUTOMATED);
        tc.setCaseautomation(ca);

        TestcaseType.Caseimportance ci = new TestcaseType.Caseimportance();
        ci.setValue(ImpTypes.fromValue(pol.caseimportance().toLowerCase()));
        tc.setCaseimportance(ci);

        TestcaseType.Caselevel cl = new TestcaseType.Caselevel();
        cl.setValue(CaseTypes.fromValue(pol.caselevel().toLowerCase()));
        tc.setCaselevel(cl);

        TestcaseType.Caseposneg cpn = new TestcaseType.Caseposneg();
        cpn.setValue(PosnegTypes.fromValue(pol.caseposneg().toLowerCase()));
        tc.setCaseposneg(cpn);

        TestcaseType.Testtype tt = new TestcaseType.Testtype();
        tt.setValue(TestTypes.fromValue(pol.testtype().toLowerCase()));
        tc.setTesttype(tt);

        tc.setWorkitemId(pol.testCaseID());
        tc.setWorkitemType(WiTypes.TEST_CASE);

        /**
         * TODO: add the Requirements
         */
        Requirement[] reqs = pol.reqs();

        return tc;
    }

    /**
     * Given the Requirement annotation data, do the following:
     *
     * - If ID exists:
     *   - Check that requirements.xml.path/class/methodName.xml exists
     *     - If it does not, generate one and call requestRequirementImporter
     *       - Wait for return value to get Requirement ID
     *     - If it does, verify the XML has Polarion ID
     *       - Verify that the Polarion ID matches the method
     * - If ID does not exist:
     *   - Generate XML request for WorkItem importer
     *   - Wait for return value to get the Requirement ID
     * @param reqs
     */
    private void processRequirement(Meta<Requirement> reqs) {

    }

    /**
     * Loads configuration data from the following in increasing order of precedence:
     *
     * - resources/polarize.properties
     * - ~/.polarize/polarize.properties
     * - Java -D options
     *
     * Two of the more important properties are requirements.xml.path and testcase.xml.path.  These fields describe
     * where the XML equivalent of the annotations will be stored.  Normally, this will be some path inside of the
     * project that will be scanned for annotations.  This is because polarize will generate the XML descriptions if
     * they dont exist, and it is better to have these xml files under source control.  When the processor needs to
     * generate an XML description based on the annotation data, it will do the following:
     *
     * - If processing @Requirement, look for/generate requirements.xml.path/class/methodName.xml
     * - If processing @Polarion, look for/generate testcase.xml.path/class/methodName.xml
     */
    private void loadConfiguration() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("polarize.properties");
        Properties props = new Properties();

        try {
            props.load(is);
            reqPath = props.getProperty("requirements.xml.path", "/tmp/reqs");
            tcPath = props.getProperty("testcases.xml.path", "/tmp/tcs");
        } catch (IOException e) {
            this.logger.info("Could not load polarize.properties.  Trying ~/.polarize/polarize.properties");
        }

        try {
            String homeDir = System.getProperty("user.home");
            BufferedReader rdr;
            rdr = Files.newBufferedReader(FileSystems.getDefault().getPath(homeDir + "/.polarize/polarize.properties"));
            props.load(rdr);
            reqPath = props.getProperty("requirements.xml.path", "/tmp/reqs");
            tcPath = props.getProperty("testcases.xml.path", "/tmp/tcs");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            reqPath = System.getProperty("requirements.xml.path");
        } catch (Exception ex) {
            this.logger.info(String.format("No -Drequirements.xml.path set.  reqPath is %s", this.reqPath));
        }

        try {
            tcPath = System.getProperty("testcases.xml.path");
        } catch (Exception ex) {
            this.logger.info(String.format("No -Dtestcases.xml.path set.  tcPath is %s", this.tcPath));
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        System.out.println("In init() method");
        super.init(env);
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.msgr = env.getMessager();
        this.logger = LoggerFactory.getLogger(PolarionProcessor.class);

        this.loadConfiguration();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> anns = new LinkedHashSet<>();
        anns.add(Polarion.class.getCanonicalName());
        anns.add(Requirement.class.getCanonicalName());
        anns.add(Test.class.getCanonicalName());
        return anns;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void errorMsg(Element elem, String msg, Object... args) {
        this.msgr.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), elem);
    }
}
