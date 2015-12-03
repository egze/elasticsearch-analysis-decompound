package org.xbib.elasticsearch.index.analysis;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;

import org.junit.Test;
import org.xbib.elasticsearch.plugin.analysis.decompound.AnalysisDecompoundPlugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class DecompoundTokenFilterTests {

    @Test
    public void testExampleFromReadme() throws IOException {
        String source = "Die Jahresfeier der Rechtsanwaltskanzleien auf dem Donaudampfschiff hat viel Ökosteuer gekostet";
        String[] expected = {
            "Die",
            "Die",
            "Jahresfeier",
            "Jahr",
            "feier",
            "der",
            "der",
            "Rechtsanwaltskanzleien",
            "Recht",
            "anwalt",
            "kanzlei",
            "auf",
            "auf",
            "dem",
            "dem",
            "Donaudampfschiff",
            "Donau",
            "dampf",
            "schiff",
            "hat",
            "hat",
            "viel",
            "viel",
            "Ökosteuer",
            "Ökosteuer",
            "gekostet",
            "gekosten"
        };
        AnalysisService analysisService = createAnalysisService("org/xbib/elasticsearch/index/analysis/decompound_analysis.json");
        TokenFilterFactory tokenFilter = analysisService.tokenFilter("decomp");
        Tokenizer tokenizer = new StandardTokenizer(new StringReader(source));
        assertSimpleTSOutput(tokenFilter.create(tokenizer), expected);
    }

    @Test
    public void ignoreKeywordsByDefault() throws IOException {
        String source = "Schlüsselwort";
        String[] expected = {
            "Schlüsselwort",
            "Schlüssel",
            "wort"
        };
        AnalysisService analysisService = createAnalysisService("org/xbib/elasticsearch/index/analysis/keywords_analysis.json");
        Analyzer analyzer = analysisService.analyzer("decompounding_default");
        assertNotNull(analyzer);
        assertSimpleTSOutput(analyzer.tokenStream("test-field", source), expected);
    }

    @Test
    public void testRespectKeywords() throws IOException {
        String source = "Schlüsselwort";
        String[] expected = {
                "Schlüsselwort"
        };
        AnalysisService analysisService = createAnalysisService("org/xbib/elasticsearch/index/analysis/keywords_analysis.json");
        Analyzer analyzer = analysisService.analyzer("with_keywords");
        assertNotNull(analyzer);
        assertSimpleTSOutput(analyzer.tokenStream("test-field", source), expected);
    }

    @Test
    public void testDisablingRespectKeywords() throws IOException {
        String source = "Schlüsselwort";
        String[] expected = {
                "Schlüsselwort",
                "Schlüssel",
                "wort"
        };
        AnalysisService analysisService = createAnalysisService("org/xbib/elasticsearch/index/analysis/keywords_analysis.json");
        Analyzer analyzer = analysisService.analyzer("with_keywords_disabled");
        assertNotNull(analyzer);
        assertSimpleTSOutput(analyzer.tokenStream("test-field", source), expected);
    }

    @Test
    public void testWithSubwordsOnly() throws IOException {
        String source = "Das ist ein Schlüsselwort, ein Bindestrichwort";
        String[] expected = {
                "Da",
                "ist",
                "ein",
                "Schlüssel",
                "wort",
                "ein",
                "Bindestrich",
                "wort"
        };
        AnalysisService analysisService = createAnalysisService("org/xbib/elasticsearch/index/analysis/keywords_analysis.json");
        Analyzer analyzer = analysisService.analyzer("with_subwords_only");
        assertNotNull(analyzer);
        assertSimpleTSOutput(analyzer.tokenStream("test-field", source), expected);
    }

    private AnalysisService createAnalysisService(String configFilePath) {
        Settings settings = ImmutableSettings.settingsBuilder().loadFromClasspath(configFilePath).build();
        Index index = new Index("test");
        Injector parentInjector = new ModulesBuilder().add(
                new SettingsModule(settings),
                new EnvironmentModule(new Environment(settings)),
                new IndicesAnalysisModule()
        ).createInjector();
        AnalysisModule analysisModule = new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class));
        new AnalysisDecompoundPlugin(settings).onModule(analysisModule);
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                analysisModule
        ).createChildInjector(parentInjector);
        return injector.getInstance(AnalysisService.class);
    }

    private static void assertSimpleTSOutput(TokenStream stream, String[] expected) throws IOException {
        stream.reset();
        CharTermAttribute termAttr = stream.getAttribute(CharTermAttribute.class);
        assertNotNull(termAttr);
        int i = 0;
        while (stream.incrementToken()) {
            assertTrue(i < expected.length);
            assertEquals(expected[i++], termAttr.toString());
        }
        assertEquals(i, expected.length);
    }
}
