package edu.lab.review;

import edu.lab.review.agent.BaseAgent;
import edu.lab.review.agent.LogicAgent;
import edu.lab.review.agent.SecurityAgent;
import edu.lab.review.agent.StyleAgent;
import edu.lab.review.agent.SummaryAgent;
import edu.lab.review.agent.SupervisorAgent;
import edu.lab.review.analysis.InputLoader;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class ReviewLabApplication {

    private ReviewLabApplication() {
    }

    public static void main(String[] args) {
        configureLogging();
        Logger log = LoggerFactory.getLogger(ReviewLabApplication.class);
        try {
            Options options = Options.parse(args);
            if (options.help) {
                printUsage();
                return;
            }
            if (options.timeout != null) {
                System.setProperty("review.timeoutSeconds", options.timeout);
            }
            if (options.delay != null) {
                System.setProperty("analysis.delayMs", options.delay);
            }
            LabConfig config = LabConfig.load(options.envFile);
            log.info("Lab config: {}", config);

            switch (options.role) {
                case "all" -> {
                    runAll(config, options, log);
                    System.exit(0);
                }
                case "supervisor" -> {
                    runSupervisor(config, options, log);
                    System.exit(0);
                }
                case "style", "logic", "security", "summary" -> runWorker(config, options.role, log);
                default -> throw new IllegalArgumentException("Unknown role: " + options.role);
            }
        } catch (Exception e) {
            log.error("Review lab failed", e);
            System.exit(1);
        }
    }

    private static void runAll(LabConfig config, Options options, Logger log) throws Exception {
        RocketMqFacade facade = new RocketMqFacade(config);
        List<BaseAgent> agents = new ArrayList<>();
        try {
            agents.add(new StyleAgent(config, facade));
            agents.add(new LogicAgent(config, facade));
            agents.add(new SecurityAgent(config, facade));
            agents.add(new SummaryAgent(config, facade));
            for (BaseAgent agent : agents) {
                agent.start();
            }
            log.info("[all] {} agents started; waiting for consumer readiness", agents.size());
            Thread.sleep(2_000);

            InputLoader.LoadedInput input = InputLoader.load(options.file, options.pr, options.code,
                options.language);
            log.info("[all] review input: type={} title={} language={} lines={}",
                input.type(), input.title(), input.language(), input.code().lines().count());
            new SupervisorAgent(config, facade).run(input, options.session);
        } finally {
            for (BaseAgent agent : agents) {
                agent.close();
            }
            facade.close();
        }
    }

    private static void runSupervisor(LabConfig config, Options options, Logger log) throws Exception {
        InputLoader.LoadedInput input = InputLoader.load(options.file, options.pr, options.code, options.language);
        try (RocketMqFacade facade = new RocketMqFacade(config)) {
            new SupervisorAgent(config, facade).run(input, options.session);
            log.info("[supervisor] finished");
            Thread.sleep(500);
        }
    }

    private static void runWorker(LabConfig config, String role, Logger log) throws Exception {
        RocketMqFacade facade = new RocketMqFacade(config);
        BaseAgent agent = switch (role) {
            case "style" -> new StyleAgent(config, facade);
            case "logic" -> new LogicAgent(config, facade);
            case "security" -> new SecurityAgent(config, facade);
            case "summary" -> new SummaryAgent(config, facade);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };
        agent.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agent.close();
            facade.close();
        }));
        log.info("[{}] running. Press Ctrl+C to stop.", role);
        agent.await();
    }

    private static void configureLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", System.getProperty(
            "org.slf4j.simpleLogger.defaultLogLevel", "info"));
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.rocketmq", "warn");
        System.setProperty("org.slf4j.simpleLogger.log.io.a2a", "warn");
    }

    private static void printUsage() {
        System.out.println("""
            RocketMQ Multi-Agent Code Review Lab

            Usage:
              java -jar rocketmq-multiagent-review-1.0.0.jar [options]

            Options:
              --role <all|supervisor|style|logic|security|summary>  default: all
              --file <path>        review a local source file
              --pr <url>           review a GitHub PR diff (requires public repo or GITHUB_TOKEN)
              --code <snippet>     review inline code
              --language <lang>    source language (default: java)
              --session <id>       reuse a session id (student id prefix is enforced)
              --env-file <path>    load RMQ_* settings from a file (e.g. .env)
              --timeout <seconds>  per-phase result timeout (default: 120)
              --delay <ms>         simulated agent reasoning delay (default: 600)
              --help               show this help

            Environment / env-file keys:
              STUDENT_ID, RMQ_ENDPOINT, RMQ_NAMESPACE, RMQ_ACCESS_KEY, RMQ_SECRET_KEY,
              RMQ_SSL, REPORT_DIR, REVIEW_TIMEOUT_SECONDS, ANALYZE_DELAY_MS

            Examples:
              java -jar target/rocketmq-multiagent-review-1.0.0.jar --file samples/InsecureOrderService.java
              java -jar target/rocketmq-multiagent-review-1.0.0.jar --pr https://github.com/owner/repo/pull/123
              java -jar target/rocketmq-multiagent-review-1.0.0.jar --role style
            """);
    }

    private static final class Options {
        private String role = "all";
        private String file;
        private String pr;
        private String code;
        private String language = "java";
        private String session;
        private String envFile;
        private String timeout;
        private String delay;
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--role" -> options.role = next(args, ++i, arg);
                    case "--file" -> options.file = next(args, ++i, arg);
                    case "--pr" -> options.pr = next(args, ++i, arg);
                    case "--code" -> options.code = next(args, ++i, arg);
                    case "--language" -> options.language = next(args, ++i, arg);
                    case "--session" -> options.session = next(args, ++i, arg);
                    case "--env-file" -> options.envFile = next(args, ++i, arg);
                    case "--timeout" -> options.timeout = next(args, ++i, arg);
                    case "--delay" -> options.delay = next(args, ++i, arg);
                    case "--help", "-h" -> options.help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            return options;
        }

        private static String next(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }
    }
}