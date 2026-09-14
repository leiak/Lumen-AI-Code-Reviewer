package com.review.council;

import com.review.council.cli.ReviewCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

@SpringBootApplication
public class CouncilApplication {
    public static void main(String[] args) {
        // Detect: 'serve' → web mode; everything else → CLI mode
        boolean serveMode = args.length > 0 && args[0].equals("serve");

        SpringApplication app = new SpringApplication(CouncilApplication.class);
        if (!serveMode) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        var ctx = app.run(args);
        try {
            if (serveMode) {
                System.out.println("Web server started. Use the API at http://localhost:8080");
                System.out.println("Press Ctrl+C to stop.");
                return; // keep server running
            }
            var cli = ctx.getBean(ReviewCli.class);
            CommandLine.IFactory factory = new SpringCommandFactory(ctx);
            int code = new CommandLine(cli, factory).execute(args);
            System.exit(code);
        } finally {
            if (!serveMode) ctx.close();
        }
    }

    /** Resolves picocli subcommand instances from the Spring context. */
    static class SpringCommandFactory implements CommandLine.IFactory {
        private final ApplicationContext ctx;
        SpringCommandFactory(ApplicationContext ctx) { this.ctx = ctx; }
        @Override public <K> K create(Class<K> cls) throws Exception {
            try { return ctx.getBean(cls); }
            catch (Exception e) { return cls.getDeclaredConstructor().newInstance(); }
        }
    }
}
