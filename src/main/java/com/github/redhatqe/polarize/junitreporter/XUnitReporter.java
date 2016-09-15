package com.github.redhatqe.polarize.junitreporter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.CIBusListener;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.exceptions.XMLDescriptionError;
import com.github.redhatqe.polarize.exceptions.XMLUnmarshallError;
import com.github.redhatqe.polarize.importer.ImporterRequest;
import com.github.redhatqe.polarize.importer.xunit.*;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.schema.WorkItem;
import com.github.redhatqe.polarize.utils.Tuple;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyException;
import java.util.*;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Class that handles junit report generation for TestNG
 *
 * Use this class when running TestNG tests as -reporter XUnitReporter.  It can be
 * configured through the reporter.properties file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitReporter implements IReporter {
    private final static Logger logger = LoggerFactory.getLogger(XUnitReporter.class);
    private static Map<String, String> polarizeConfig = com.github.redhatqe.polarize.Configurator.loadConfiguration();
    private final static File defaultPropertyFile =
            new File(System.getProperty("user.home") + "/.polarize/reporter.properties");
    private final static ReporterConfig config = XUnitReporter.configure();

    public static ReporterConfig configure() {
        Properties props = XUnitReporter.getProperties();

        ReporterConfig config = new ReporterConfig();
        try {
            config.setAuthor(XUnitReporter.validateNonEmptyProperty("author-id", props));
            config.setProjectID(XUnitReporter.validateNonEmptyProperty("project-id", props));
        } catch (KeyException e) {
            e.printStackTrace();
        }

        config.setDryRun(Boolean.valueOf(props.getProperty("dry-run")));
        config.setIncludeSkipped(Boolean.valueOf(props.getProperty("include-skipped")));
        config.setResponseName(props.getProperty("response-name"));
        config.setSetTestRunFinished(Boolean.valueOf(props.getProperty("set-testrun-finished")));
        config.setTestrunID(props.getProperty("testrun-id"));
        config.setTestrunTitle(props.getProperty("testrun-title"));
        config.setTestcasesXMLPath(props.getProperty("testcases-xml-path"));
        config.setRequirementsXMLPath(props.getProperty("requirements-xml-path"));
        config.setResponseTag(props.getProperty("response-tag"));

        String fields = props.getProperty("custom-fields");
        config.setCustomFields(new HashMap<>());
        if (!fields.equals("")) {
            String[] flds = fields.split(",");
            Arrays.stream(flds)
                    .forEach(f -> {
                        String[] kvs = f.split("=");
                        config.setCustomFields(kvs[0], kvs[1]);
                    });
        }

        return config;
    }

    public static Properties getProperties() {
        Properties props = new Properties();
        Map<String, String> envs = System.getenv();
        if (envs.containsKey("XUNIT_IMPORTER_CONFIG")) {
            String path = envs.get("XUNIT_IMPORTER_CONFIG");
            File fpath = new File(path);
            if (fpath.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(fpath);
                    props.load(fis);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (XUnitReporter.defaultPropertyFile.exists()){
            try {
                FileInputStream fis = new FileInputStream(XUnitReporter.defaultPropertyFile);
                props.load(fis);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            InputStream is = XUnitReporter.class.getClassLoader().getResourceAsStream("reporter.properties");
            try {
                props.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }


    private static String validateNonEmptyProperty(String key, Properties props) throws KeyException {
        if (!props.containsKey(key)) {
            throw new KeyException(String.format("Missing %s in reporter.properties", key));
        }

        String val = props.getProperty(key);
        if (val.equals(""))
            throw new KeyException(String.format("%s can not be empty string", val));
        return val;
    }

    /**
     * Generates a modified xunit result that can be used for the XUnit Importer
     *
     * Example of a modified junit file:
     *
     * <testsuites>
     *
     *     <testsuite>
     *         <testcase>
     *
     *         </testcase>
     *     </testsuite>
     * </testsuites>
     *
     * @param xmlSuites passed by TestNG
     * @param suites passed by TestNG
     * @param outputDirectory passed by TestNG.  configurable?
     */
    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        Testsuites tsuites = XUnitReporter.getTestSuiteInfo(config.getResponseTag());
        List<Testsuite> tsuite = tsuites.getTestsuite();

        // Get information for each <testsuite>
        suites.forEach(suite -> {
            String name = suite.getName();
            Map<String, ISuiteResult> results = suite.getResults();
            List<Testsuite> collected = results.entrySet().stream()
                    .map(es -> {
                        Testsuite ts = new Testsuite();
                        String key = es.getKey();
                        ISuiteResult result = es.getValue();
                        ITestContext ctx = result.getTestContext();

                        ts.setName(key);
                        ts.setFailures(Integer.toString(ctx.getFailedTests().size()));
                        ts.setSkipped(Integer.toString(ctx.getSkippedTests().size()));
                        ts.setTests(Integer.toString(ctx.getAllTestMethods().length));
                        Date start = ctx.getStartDate();
                        Date end = ctx.getEndDate();
                        float duration = (end.getTime() - start.getTime())/ 1000;
                        ts.setTime(Float.toString(duration));

                        List<Testcase> tests = ts.getTestcase();
                        List<Testcase> after = XUnitReporter.getMethodInfo(this, suite, tests);
                        return ts;
                    })
                    .collect(Collectors.toList());
            tsuite.addAll(collected);
        });

        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        File reportPath = new File(outputDirectory + "/testng-polarion.xml");
        String url = polarizeConfig.get("polarion.url") + polarizeConfig.get("importer.xunit.endpoint");
        String user = polarizeConfig.get("kerb.user");
        String pw = polarizeConfig.get("kerb.pass");
    }

    /**
     * Makes an Xunit importer REST call
     * </p>
     * The actual response will come over the CI Message bus, not in the body of the http response.  Note that the
     * pw is sent over basic auth and is therefore not encrypted.
     *
     * @param url The URL endpoint for the REST call
     * @param user User name to authenticate as
     * @param pw The password for the user
     * @param reportPath path to where the xml file for uploading will be marshalled to
     * @param tsuites the object that will be marshalled into XML
     */
    public static void sendXunitImportRequest(String url, String user, String pw, File reportPath, Testsuites tsuites) {
        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        // File reportPath = new File(outputDirectory + "/testng-polarion.xml");
        CloseableHttpResponse resp =
                ImporterRequest.post(tsuites, Testsuites.class, url, reportPath.toString(), user, pw);
        HttpEntity entity = resp.getEntity();
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            System.out.println(bfr.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(resp.toString());
    }

    public static void sendXunitImportRequest(String url, String user, String pw, File reportPath, String selector) {
        CloseableHttpResponse resp = ImporterRequest.post(url, reportPath, user, pw);
        HttpEntity entity = resp.getEntity();
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            System.out.println(bfr.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(resp.toString());

        CIBusListener bl = new CIBusListener(com.github.redhatqe.polarize.Configurator.loadConfiguration());
        Optional<Tuple<Connection, Message>> maybeConn = bl.waitForMessage(selector);
        if (!maybeConn.isPresent()) {
            bl.logger.error("No Connection object found");
            return;
        }

        Tuple<Connection, Message> tuple = maybeConn.get();
        Message msg = tuple.second;
        try {
            ObjectNode root = bl.parseMessage(msg);
            XUnitReporter.logger.info(root.textValue());

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the status for a Testcase object given values from ITestResult
     * 
     * @param result
     * @param tc
     */
    public static void getStatus(ITestResult result, Testcase tc) {
        int status = result.getStatus();
        switch(status) {
            case ITestResult.FAILURE:
                Failure fail = new Failure();
                tc.getFailure().add(fail);
                break;
            case ITestResult.SKIP:
                tc.setSkipped("true");
                break;
            case ITestResult.SUCCESS:
                tc.setStatus("success");
                break;
            default:
                XUnitReporter.logger.warn("Unused status for testcase");
                break;
        }
    }

    public static List<Testcase> getMethodInfo(XUnitReporter xUnitReporter, ISuite suite, List<Testcase> testcases) {
        // Get information for each <testcase>
        List<IInvokedMethod> invoked = suite.getAllInvokedMethods();
        for(IInvokedMethod meth: invoked) {
            Testcase testcase = new Testcase();
            testcases.add(testcase);

            ITestNGMethod fn = meth.getTestMethod();
            XmlTest test = fn.getXmlTest();
            ITestResult result = meth.getTestResult();
            result.getStartMillis();

            ITestClass clz = fn.getTestClass();
            String methname = fn.getMethodName();
            String classname = clz.getName();

            testcase.setName(methname);
            testcase.setClassname(classname);

            // Create the <properties> element, and all the child <property> sub-elements
            com.github.redhatqe.polarize.importer.xunit.Properties props =
                    new com.github.redhatqe.polarize.importer.xunit.Properties();
            List<Property> tcProps = props.getProperty();

            // Look up in the XML description file the qualifiedName to get the Polarion ID
            File xmlDesc = XUnitReporter.getXMLDescFile(classname, methname);
            String id = XUnitReporter.getPolarionIDFromXML(TestDefinition.class, xmlDesc);
            Property polarionID = XUnitReporter.createProperty("polarion-testcase-id", id);
            tcProps.add(polarionID);

            // Get status
            XUnitReporter.getStatus(result, testcase);

            // Get all the iteration data
            Object[] params = result.getParameters();
            for(int x = 0; x < params.length; x++) {
                Property param = new Property();
                param.setName("polarion-parameter-" + Integer.toString(x));
                param.setValue(params[x].toString());
                tcProps.add(param);
            }
            testcase.setProperties(props);
        }
        return testcases;
    }

    public static Testsuites getTestSuiteInfo(String responseName) {
        Testsuites tsuites = new Testsuites();
        com.github.redhatqe.polarize.importer.xunit.Properties props =
                new com.github.redhatqe.polarize.importer.xunit.Properties();
        List<Property> properties = props.getProperty();

        Property author = XUnitReporter.createProperty("polarion-user-id", config.getAuthor());
        properties.add(author);

        Property projectID = XUnitReporter.createProperty("polarion-project-id", config.getProjectID());
        properties.add(projectID);

        Property testRunFinished = XUnitReporter.createProperty("polarion-set-testrun-finished",
                config.getSetTestRunFinished().toString());
        properties.add(testRunFinished);

        Property dryRun = XUnitReporter.createProperty("polarion-dry-run", config.getDryRun().toString());
        properties.add(dryRun);

        Property includeSkipped = XUnitReporter.createProperty("polarion-include-skipped",
                config.getIncludeSkipped().toString());
        properties.add(includeSkipped);

        Configurator cfg =
                XUnitReporter.createConditionalProperty("polarion-response", responseName, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-custom", null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-testrun-title", null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty("polarion-testrun-id", null, properties);
        cfg.set();

        tsuites.setProperties(props);
        return tsuites;
    }

    /**
     * Simple setter for a Property
     *
     * TODO: replace this with a lambda
     *
     * @param name
     * @param value
     * @return
     */
    private static Property createProperty(String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    @FunctionalInterface
    interface Configurator {
        void set();
    }

    private static Configurator
    createConditionalProperty(String name, String value, List<Property> properties) {
        Configurator cfg;
        Property prop = new Property();
        prop.setName(name);

        switch(name) {
            case "polarion-testrun-title":
                cfg = () -> {
                    if (config.getTestrunTitle().equals(""))
                        return;
                    prop.setValue(config.getTestrunTitle());
                    properties.add(prop);
                };
                break;
            case "polarion-testrun-id":
                cfg = () -> {
                    if (config.getTestrunID().equals(""))
                        return;
                    prop.setValue(config.getTestrunID());
                    properties.add(prop);
                };
                break;
            case "polarion-response":
                cfg = () -> {
                    if (config.getResponseName().equals(""))
                        return;
                    prop.setName("polarion-response-" + value);
                    prop.setValue(config.getResponseName());
                    properties.add(prop);
                };
                break;
            case "polarion-custom":
                cfg = () -> {
                    Map<String, String> customFields = config.getCustomFields();
                    if (customFields.isEmpty())
                        return;
                    customFields.entrySet().forEach(entry -> {
                        String key = "polarion-custom-" + entry.getKey();
                        String val = entry.getValue();
                        Property p = new Property();
                        p.setName(key);
                        p.setValue(val);
                        properties.add(p);
                    });
                };
                break;
            default:
                cfg = null;
        }
        return cfg;
    }

    /**
     * Given the fully qualified method name, find the xml description file
     * @param className
     * @param methName
     */
    private static File getXMLDescFile(String className, String methName) {
        String tcXMLPath = config.getTestcasesXMLPath();
        String projID = config.getProjectID();

        Path path = Paths.get(tcXMLPath, projID, className, methName + ".xml");
        File xmlDesc = path.toFile();
        if(!xmlDesc.exists())
            throw new XMLDescriptionError();

        return xmlDesc;
    }

    private static <T> String getPolarionIDFromXML(Class<T> t, File xmldesc) {
        JAXBReporter jaxb = new JAXBReporter();
        if (t == TestDefinition.class) {
            Optional<com.github.redhatqe.polarize.importer.testcase.Testcase> tc;
            tc = IJAXBHelper.unmarshaller(com.github.redhatqe.polarize.importer.testcase.Testcase.class, xmldesc,
                    jaxb.getXSDFromResource(com.github.redhatqe.polarize.importer.testcase.Testcase.class));
            if (!tc.isPresent())
                throw new XMLUnmarshallError();
            com.github.redhatqe.polarize.importer.testcase.Testcase tcase = tc.get();
            if (tcase.getId() == null)
                return "";
            return tcase.getId();
        }
        else if (t == Requirement.class) {
            Optional<WorkItem> wi;
            wi = IJAXBHelper.unmarshaller(WorkItem.class, xmldesc, jaxb.getXSDFromResource(WorkItem.class));
            if (wi.isPresent())
                return wi.get().getRequirement().getId();
            else
                return "";
        }
        else
            throw new XMLDescriptionError();
    }

    /**
     * Args are: url, user, pw, path to xml, selector
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            XUnitReporter.logger.error("url, user, pw, path to xml");
            return;
        }
        File xml = new File(args[3]);
        XUnitReporter.sendXunitImportRequest(args[0], args[1], args[2], xml, args[4]);
    }
}
