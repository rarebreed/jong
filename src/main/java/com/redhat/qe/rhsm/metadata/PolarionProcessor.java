package com.redhat.qe.rhsm.metadata;

import com.redhat.qe.rhsm.FileHelper;
import com.redhat.qe.rhsm.JAXBHelper;
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
import javax.xml.bind.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the Annotation processor that will look for @Polarion and @Requirement annotations
 *
 * While compiling code, it will find methods (or classes for @Requirement) that are annotated and generate an XML
 * description which is suitable to be consumed by the WorkItem Importer.  The polarize.properties is used to set where
 * the generated XML files will go and be looked for.
 *
 * Created by stoner on 5/16/16.
 */
public class PolarionProcessor extends AbstractProcessor {
    private Types types;
    private Filer filer;
    private Elements elements;
    private Messager msgr;
    private Logger logger;
    private String reqPath;
    private String tcPath;
    private Map<String, Meta<Requirement>> methToRequirement;
    // map of qualified name -> {projectID: meta}
    private Map<String, Map<String,
                            Meta<Requirement>>> methToProjectReq;
    private Map<String, Map<String,
                            Meta<Polarion>>> methToProjectPol;
    private Map<String, String> methNameToTestNGDescription;

    /**
     * Recursive function that will get the fully qualified name of a method.
     *
     * @param elem Element object to recursively ascend up
     * @param accum a string which accumulate the qualified name
     * @param m Meta object whose values will be initialized as the function recurses
     * @return the fully qualified path of the starting Element
     */
    private String getTopLevel(Element elem, String accum, Meta m) {
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


    /**
     * Creates a list of Meta objects from a list of Element objects
     *
     * @param elements list of Elements
     * @param ann an Annotation class (eg Polarion.class)
     * @param <T> type that is of an Annotation
     * @return a list of Metas of type T
     */
    private <T extends Annotation> List<Meta<T>> makeMeta(List<? extends Element> elements, Class<T> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Meta m = new Meta<T>();
            String full = this.getTopLevel(e, "", m);
            this.logger.info(String.format("Fully qualified name is %s", full));
            m.qualifiedName = full;
            m.annotation = e.getAnnotation(ann);
            metas.add(m);
        }
        return metas;
    }

    /**
     * Creates a List of Meta types from a Requirements annotation
     *
     * TODO: Figure out how to parameterize this.  The ann variable is already Requirements.class
     * @param elements a list of Elements
     * @param ann an annotation class (eg. Requirement.class)
     * @param <T> type that extends an Annotation
     * @return a list of Metas of type T
     */
    private <T extends Annotation> List<Meta<T>>
    makeMetaFromRequirements(List<? extends Element> elements,
                             Class<? extends Annotation> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Requirements container = (Requirements) e.getAnnotation(ann);
            for(Requirement r: container.value()) {
                Meta m = new Meta<T>();
                String full = this.getTopLevel(e, "", m);
                this.logger.info(String.format("Fully qualified name is %s", full));
                m.qualifiedName = full;
                m.annotation = r;
                metas.add(m);
            }
        }
        return metas;
    }

    private <T> List<Meta<T>>
    makeMetaFromPolarions(List<? extends Element> elements,
                          Class<? extends Annotation> ann){
        List<Meta<T>> metas = new ArrayList<>();
        for(Element e : elements) {
            Polarions container = (Polarions) e.getAnnotation(ann);
            for(Polarion r: container.value()) {
                Meta m = new Meta<T>();
                String full = this.getTopLevel(e, "", m);
                this.logger.info(String.format("Fully qualified name is %s", full));
                m.qualifiedName = full;
                m.annotation = r;
                metas.add(m);
            }
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

        this.logger.info("Getting all the @Requirement annotations which have been repeated");
        TreeSet<ElementKind> allowed = new TreeSet<>();
        allowed.add(ElementKind.CLASS);
        String err = "Can only annotate classes with @Requirements";
        List<? extends Element> repeatedAnns = this.getAnnotations(roundEnvironment, Requirements.class, allowed, err);
        List<Meta<Requirement>> requirements = this.makeMetaFromRequirements(repeatedAnns, Requirements.class);

        // Get all the @Requirement annotated on a class that are not repeated. We will use the information here to
        // generate the XML for requirements.  If a @Polarion annotated test method has an empty reqs, we will look in
        // methToRequirements for the associated Requirement.
        allowed = new TreeSet<>();
        allowed.add(ElementKind.CLASS);
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate classes or methods with @Requirement";
        List<? extends Element> reqAnns = this.getAnnotations(roundEnvironment, Requirement.class, allowed, err);
        requirements.addAll(this.makeMeta(reqAnns, Requirement.class));
        this.methToProjectReq.putAll(this.createMethodToMetaRequirementMap(requirements));

        // Get all the @Polarion annotations
        // Make a list of Meta types that store the fully qualified name of every @Polarion annotated
        // method.  We will use this to create a map of qualified_name => Polarion Annotation
        allowed = new TreeSet<>();
        allowed.add(ElementKind.METHOD);
        err = "Can only annotate methods with @Polarion";
        List<? extends Element> polAnns = this.getAnnotations(roundEnvironment, Polarion.class, allowed, err);
        List<Meta<Polarion>> metas = this.makeMeta(polAnns, Polarion.class);

        // Get all the @Polarion annotations which have been repeated on a single element.
        List<? extends Element> pols = this.getAnnotations(roundEnvironment, Polarions.class, allowed, err);
        metas.addAll(this.makeMetaFromPolarions(pols, Polarions.class));

        this.methToProjectPol = this.createMethodToMetaPolarionMap(metas);


        // Get all the @Test annotations in order to get the description
        Map<String, String> methToDescription = this.getTestAnnotations(roundEnvironment);
        this.methNameToTestNGDescription.putAll(methToDescription);

        // We now have the mapping from qualified name to annotation.  So, process each TestCase object
        List<Testcase> tests = this.methToProjectPol.entrySet().stream()
                .flatMap(es -> {
                    String qualifiedName = es.getKey();
                    @Nonnull String desc = methNameToTestNGDescription.get(qualifiedName);
                    return es.getValue().entrySet().stream()
                            .map(val -> {
                                Meta<Polarion> meta = val.getValue();
                                return this.processTestCase(meta, desc);
                            })
                            .collect(Collectors.toList()).stream();
                })
                .collect(Collectors.toList());

        // Run processRequirement on Requirements annotated at the class level.  Since these are annotated at the
        // class level they must have the projectID
        List<ReqType> reqList = methToRequirement.entrySet().stream()
                .map(e -> {
                    Meta<Requirement> m = e.getValue();
                    if (m.annotation.project().equals("")) {
                        String errMsg = "When annotating a class with @Requirement, the project value must be set";
                        this.msgr.printMessage(Diagnostic.Kind.ERROR, String.format(errMsg));
                        return null;
                    }
                    return this.processRequirement(m);
                })
                .collect(Collectors.toList());
        for(ReqType rt: reqList) {
            if (rt == null)
                return false;
        }

        // Convert the TestcaseType objects to XML
        // TODO: figure out how to get the project-id
        TestCaseMetadata tcmd = new TestCaseMetadata();
        tcmd.setProjectId(ProjectVals.RED_HAT_ENTERPRISE_LINUX_7);
        tcmd.setDryRun(true);

        TestCaseMetadata.Workitems wis = new TestCaseMetadata.Workitems();
        List<Testcase> tcs = wis.getTestcase();
        tcs.addAll(tests);
        tcmd.setWorkitems(wis);

        try {
            JAXBContext jaxbc = JAXBContext.newInstance(TestCaseMetadata.class);
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(tcmd, new File("/tmp/testing.xml"));
            marshaller.marshal(tcmd, System.out);
        } catch (PropertyException pe){
            pe.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        return true;
    }


    public void createTestCaseXML(Testcase tc, File path) {
        WorkItem wi = new WorkItem();
        wi.setTestcase(tc);
        wi.setProjectId(tc.getProject());
        wi.setType(WiTypes.TEST_CASE);
        JAXBHelper.marshaller(wi, path);
    }

    private Map<String, Map<String, Meta<Polarion>>>
    createMethodToMetaPolarionMap(List<Meta<Polarion>> metas) {
        Map<String, Map<String, Meta<Polarion>>> methods = new HashMap<>();
        Map<String, Meta<Polarion>> projects = new HashMap<>();
        for(Meta<Polarion> m: metas) {
            Polarion ann = m.annotation;
            String meth = m.qualifiedName;
            String project = ann.projectID();

            projects.put(project, m);
            if(!methods.containsKey(meth)) {
                methods.put(meth, projects);
            }
        }
        return methods;
    }

    /**
     * Creates a nested map of qualifiedName -> {projectID: Requirement}
     *
     * TODO: Figure out how to do this with reduce
     *
     * @param metas A list of Metas of type Requirement
     * @return a nested map qualifiedName to {projectId: Requirement}
     */
    private Map<String, Map<String, Meta<Requirement>>>
    createMethodToMetaRequirementMap(List<Meta<Requirement>> metas) {
        Map<String, Map<String, Meta<Requirement>>> acc = new HashMap<>();
        Map<String, Meta<Requirement>> projectToMeta = new HashMap<>();
        for(Meta<Requirement> m: metas) {
            Requirement r = m.annotation;
            String key = m.qualifiedName;
            String project = r.project();
            projectToMeta.put(project, m);
            if (!acc.containsKey(key))
                acc.put(key, projectToMeta);
        }
        return acc;
    }

    private List<? extends Element>
    getAnnotations(RoundEnvironment env,
                   Class<? extends Annotation> c,
                   Set<ElementKind> allowed,
                   String errMsg) {
        String iface = c.getSimpleName();
        return env.getElementsAnnotatedWith(c)
                .stream()
                .map(ae -> {
                    this.logger.info(String.format("Kind is %s", ae.getKind().toString()));
                    if (!allowed.contains(ae.getKind())){
                        this.errorMsg(ae, errMsg, iface);
                        return null;
                    }
                    return ae;
                })
                .filter(ae -> ae != null)
                .collect(Collectors.toList());
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


    /**
     * TODO: Looks for the xmlDesc file for a TestCase or Requirement
     *
     * @param elem
     * @return
     */
    public File getXMLFileForMethod(Element elem) {
        return null;
    }


    /**
     * TODO: Takes a feature file in gherkin style, and generates an XML file
     *
     * @param featureFile
     */
    private void featureToRequirement(String featureFile) {

    }

    /**
     * Examines a Polarion object to obtain its values and generates an XML file if needed.
     *
     * @param meta
     * @param description
     * @return
     */
    private Testcase processTestCase(Meta<Polarion> meta, String description) {
        Polarion pol = meta.annotation;
        Testcase tc = new Testcase();
        tc.setAuthor(pol.author());
        tc.setDescription(description);
        tc.setTitle(meta.qualifiedName);

        // For automation, let's always assume we're in draft state
        Testcase.Status status = new Testcase.Status();
        status.setValue("draft");
        tc.setStatus(status);

        Testcase.Caseautomation ca = new Testcase.Caseautomation();
        ca.setValue(AutomationTypes.AUTOMATED);
        tc.setCaseautomation(ca);

        Testcase.Caseimportance ci = new Testcase.Caseimportance();
        ci.setValue(ImpTypes.fromValue(pol.caseimportance().toLowerCase()));
        tc.setCaseimportance(ci);

        Testcase.Caselevel cl = new Testcase.Caselevel();
        cl.setValue(CaseTypes.fromValue(pol.caselevel().toLowerCase()));
        tc.setCaselevel(cl);

        Testcase.Caseposneg cpn = new Testcase.Caseposneg();
        cpn.setValue(PosnegTypes.fromValue(pol.caseposneg().toLowerCase()));
        tc.setCaseposneg(cpn);

        Testcase.Testtype tt = new Testcase.Testtype();
        tt.setValue(TestTypes.fromValue(pol.testtype().toLowerCase()));
        tc.setTesttype(tt);

        tc.setWorkitemId(pol.testCaseID());
        tc.setWorkitemType(WiTypes.TEST_CASE);

        tc.setProject(ProjectVals.fromValue(meta.annotation.projectID()));

        Requirement[] reqs = pol.reqs();
        // If reqs is empty look at the class annotated requirements contained in methToProjectReq
        if (reqs.length == 0) {
            String pkgClassname = String.format("%s.%s", meta.packName, meta.className);
            String project = tc.getProject().value();
            Meta<Requirement> r = this.methToProjectReq.get(pkgClassname).get(project);
            reqs = new Requirement[1];
            reqs[0] = r.annotation;
        }
        Testcase.Requirements treq = new Testcase.Requirements();
        List<ReqType> r = treq.getRequirement();
        for(Requirement e: reqs) {
            Meta<Requirement> m = new Meta<>(meta);
            m.annotation = e;
            ProjectVals proj = tc.getProject();
            ReqType req = this.processRequirement(m, proj);
            r.add(req);
        }

        //TODO: Check for XML Desc file

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
     * @param meta
     */
    private ReqType processRequirement(Meta<Requirement> meta) {
        Requirement r = meta.annotation;
        ReqType req = this.createReqTypeFromRequirement(r);

        Path path = FileHelper.makeXmlPath(this.reqPath, meta);
        File xmlDesc = path.toFile();
        WorkItem wi;
        if (path.toFile().exists()) {
            wi = JAXBHelper.unmarshaller(WorkItem.class, xmlDesc);
            return wi.getRequirement();
        }
        else {
            // TODO: Generate the directory path if necessary
            Path parent = path.getParent();
            Boolean success = parent.toFile().mkdirs();
        }

        if(r.id().equals("")) {
            this.logger.info("No polarionID...");
            // Check for xmlDesc.  If both the id and xmlDesc are empty strings, then we need to generate an XML file
            // based on the Requirement metadata
            if (r.xmlDesc().equals("")) {
                return this.initReqType(req, xmlDesc);
            }
            else {
                this.logger.info("Found xmlDesc value, unmarshalling...");
                if (path.toFile().exists()) {
                    wi = JAXBHelper.unmarshaller(WorkItem.class, xmlDesc);
                    return wi.getRequirement();
                }
                else {
                    this.logger.error("xmlDesc was populated, but xml file doesn't exist.  Generating desc file");
                    return this.initReqType(req, xmlDesc);
                }
            }
        }
        else {
            this.logger.info("TODO: Polarion ID was given. Chech if xmldesc has the same");
            wi = JAXBHelper.unmarshaller(WorkItem.class, xmlDesc);
            return wi.getRequirement();
        }
    }

    private ReqType initReqType(ReqType req, File xmlpath) {
        this.logger.info(String.format("Generating XML requirement descriptor in %s", xmlpath.toString()));
        WorkItem wi = new WorkItem();
        wi.setRequirement(req);
        wi.setProjectId(req.getProject());
        wi.setType(WiTypes.REQUIREMENT);

        JAXBHelper.marshaller(wi, xmlpath);
        return wi.getRequirement();
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
     * @param m
     */
    private ReqType processRequirement(Meta<Requirement> m, ProjectVals project) {
        ReqType req = this.processRequirement(m);
        req.setProject(project);
        return req;
    }

    private ReqType createReqTypeFromRequirement(Requirement r) {
        ReqType req = new ReqType();
        req.setAuthor(r.author());
        req.setDescription(r.description());
        req.setId(r.id());
        req.setPriority(r.priority());
        try {
            req.setProject(ProjectVals.fromValue(r.project()));
        } catch (Exception ex) {
            this.logger.warn("No projectID...will try from @Polarion");
        }
        req.setReqtype(r.reqtype());
        req.setSeverity(r.severity());
        return req;
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
            //e.printStackTrace();
        }

        String xmlReqPath = System.getProperty("requirements.xml.path");
        if (xmlReqPath != null)
            reqPath = xmlReqPath;

        String xmlTCPath = System.getProperty("testcases.xml.path");
        if (xmlTCPath != null)
            tcPath = xmlTCPath;
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

        this.methNameToTestNGDescription = new HashMap<>();
        this.methToRequirement = new HashMap<>();
        this.methToProjectReq = new HashMap<>();
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
