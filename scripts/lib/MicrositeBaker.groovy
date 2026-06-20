// docToolchain v4 — MicrositeBaker
//
// A self-contained replacement for jBake's Oven. It renders the microsite from
// the prepared site directory (templates/, doc/, assets/) using only:
//   * AsciidoctorJ for .adoc bodies (already a v4 runtime dependency)
//   * Groovy's SimpleTemplateEngine for the GSP templates
//
// It deliberately reproduces the exact behaviour the docToolchain templates
// rely on — and nothing more — so we can drop jBake (and with it OrientDB and
// the Groovy-3 coupling that blocks Groovy 5 / Java 25 / Apple Silicon).
//
// The template runtime mirrors jBake's org.jbake.template.GroovyTemplateEngine:
//   * the same SimpleTemplateEngine (identical ${}/<%= %> escaping),
//   * a `wrap` map that is a *view* over the per-page model (so SimpleTemplate's
//     `out` lands back in the model), and
//   * an `include` closure == jBake's doInclude: render another template against
//     the same model and the same writer.
//
// The content model mirrors jBake's parser + crawler:
//   * AsciiDoc: every `:jbake-x:` attribute is stored under BOTH `jbake-x` and
//     the stripped `x` (this is why menu.groovy reads page['jbake-menu'] while
//     header.gsp reads content.title).
//   * Markdown/HTML: the `~~~~~~`-delimited header keys are stored verbatim
//     (no stripping — matching jBake's MarkupEngine).

import org.asciidoctor.Asciidoctor
import org.asciidoctor.SafeMode
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.io.FileType

class MicrositeBaker {

    // --- inputs (set by caller) ---
    File siteDir                 // tmp/site (holds templates/, doc/, assets/, jbake.properties)
    File outputDir               // build/microsite/output
    Map rawConfig                // full dtc config (for microsite.*, jbake.*)
    List<String> asciidoctorAttributes = []
    String version = '4.0'

    // --- derived ---
    File templateFolder
    File contentFolder
    File assetFolder
    Map config                   // flattened template model config (site_*, sourceFolder, ...)
    Map<String, String> typeTemplate = [:]
    Map<String, String> typeExtension = [:]
    Asciidoctor asciidoctor
    SimpleTemplateEngine ste = new SimpleTemplateEngine()
    Map<String, Template> templateCache = [:]
    List<Map> allContent = []

    static final List<String> MARKUP_EXT =
            ['ad', 'adoc', 'asciidoc', 'md', 'markdown', 'html', 'htm', 'xhtml']

    // ===================================================================
    //  Template runtime — faithful re-implementation of jBake's engine
    // ===================================================================

    // A view over the per-page model. get() injects `include`/`renderer`;
    // put() delegates to the model so SimpleTemplate's `out` is shared with
    // included templates (exactly how jBake threads `out` through doInclude).
    static class WrapMap extends HashMap {
        Map model
        MicrositeBaker self
        Object get(Object k) {
            if (k == 'include') return self.&doInclude.curry(model)
            if (k == 'renderer') return self
            return model.get(k)
        }
        Object put(Object k, Object v) { model.put(k, v) }
        boolean containsKey(Object k) {
            k == 'include' || k == 'renderer' || model.containsKey(k)
        }
    }

    Template findTemplate(String name) {
        templateCache.computeIfAbsent(name) {
            ste.createTemplate(new File(templateFolder, name).getText('UTF-8'))
        }
    }

    void doInclude(Map model, String name) {
        renderDocument(model, name, (Writer) model.get('out'))
    }

    void renderDocument(Map model, String templateName, Writer writer) {
        def wrapped = new WrapMap(model: model, self: this)
        findTemplate(templateName).make(wrapped).writeTo(writer)
    }

    // ===================================================================
    //  Bake
    // ===================================================================

    void bake() {
        templateFolder = new File(siteDir, 'templates')
        contentFolder = new File(siteDir, 'doc')
        assetFolder = new File(siteDir, 'assets')
        outputDir.mkdirs()

        buildConfigModel()
        loadTemplateTypes()

        asciidoctor = Asciidoctor.Factory.create()
        asciidoctor.requireLibrary('asciidoctor-diagram')
        def diagramHints = loadDiagramHints()
        diagramHints?.register(asciidoctor)

        crawl()
        buildModelLists()
        renderAllContent()
        renderSpecialPages()
        copyAssets()

        diagramHints?.printHints()
        asciidoctor.close()
        println "MicrositeBaker: rendered ${allContent.size()} content pages."
    }

    // Load the DiagramToolHints helper from the same scripts/lib directory.
    private Object loadDiagramHints() {
        try {
            def libDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
            return new GroovyClassLoader(getClass().classLoader)
                    .parseClass(new File(libDir, 'DiagramToolHints.groovy')).newInstance()
        } catch (Throwable ignored) {
            return null
        }
    }

    // --- config model (site_*, sourceFolder, contentFolder, jbake props) ---
    void buildConfigModel() {
        config = [:]
        // jBake defaults the templates actually read
        config.site_host = 'http://jbake.org'
        config.feed_file = 'feed.xml'

        // jbake.properties (site.host etc.)
        def propsFile = new File(siteDir, 'jbake.properties')
        if (propsFile.exists()) {
            def p = new Properties()
            propsFile.withInputStream { p.load(it) }
            p.each { k, v -> config[((String) k).replace('.', '_')] = v }
        }

        // microsite.* -> site_*   (rich Map/List values survive)
        // jBake stored these via commons-configuration's setProperty, which
        // splits String values on the list delimiter ',' into a trimmed List
        // (a single element stays a String). The templates rely on this — e.g.
        // menu.gsp does `config.site_search.join(",")` — so we reproduce it to
        // keep the output byte-identical.
        def micrositeCfg = rawConfig.microsite ?: [:]
        micrositeCfg.each { k, v ->
            if (k in ['siteFolder', 'additionalConverters', 'customConvention']) return
            config["site_${k}".toString()] = applyListDelimiter(v == null ? '' : v)
        }
        if (config.site_menu == null) config.site_menu = [:]

        // derived file accessors the templates use
        config.sourceFolder = siteDir
        config.contentFolder = contentFolder
    }

    // commons-configuration list-delimiter emulation: a String containing ','
    // becomes a List of trimmed parts (single part stays a String).
    Object applyListDelimiter(Object v) {
        if (!(v instanceof String) || !((String) v).contains(',')) return v
        def parts = ((String) v).split(',', -1).collect { it.trim() }
        return parts.size() > 1 ? parts : parts[0]
    }

    // --- type -> template file / output extension, from jbake.properties ---
    void loadTemplateTypes() {
        // special render templates
        ['masterindex': 'index', 'archive': 'archive', 'feed': 'feed',
         'sitemap': 'sitemap', 'tag': 'tags'].each { type, key ->
            typeTemplate[type] = (config["template_${key}_file".toString()] ?: "${key}.gsp").toString()
        }
        typeExtension['masterindex'] = '.html'
        typeExtension['archive'] = '.html'
        typeExtension['feed'] = '.xml'
        typeExtension['sitemap'] = '.xml'
        typeExtension['tag'] = '.html'

        // content types: template.<type>.file (+ optional .extension)
        def propsFile = new File(siteDir, 'jbake.properties')
        if (propsFile.exists()) {
            propsFile.eachLine { line ->
                def m = (line =~ /^template\.([^.]+)\.file=(.+)$/)
                if (m) { typeTemplate[m[0][1]] = m[0][2].trim() }
                def e = (line =~ /^template\.([^.]+)\.extension=(.+)$/)
                if (e) { typeExtension[e[0][1]] = e[0][2].trim() }
            }
        }
        typeExtension = typeExtension.withDefault { '.html' }
    }

    // ===================================================================
    //  Crawl: parse every content file into a model map
    // ===================================================================

    void crawl() {
        contentFolder.traverse(type: FileType.FILES) { File file ->
            def ext = extOf(file.name)
            if (!(ext in MARKUP_EXT)) return
            if (file.name.startsWith('_') || file.name.startsWith('.')) return

            def content = parseFile(file, ext)
            if (content == null) return
            if (!content.type || !content.status) return   // not a real content doc

            def rel = contentFolder.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            // The in-model uri always uses the default .html extension. The
            // type's output-extension override (e.g. lunrjsindex -> .js) and the
            // draft suffix only affect the file actually written to disk.
            def uri = rel.replaceAll(/\.[^.\/]+$/, '.html')
            content.uri = uri
            content.sourceuri = uri
            content.file = file.name
            content.rootpath = null     // header.gsp computes it from sourceuri
            def outExt = typeExtension[content.type]
            content.outputUri = (content.status == 'draft') ?
                    rel.replaceAll(/\.[^.\/]+$/, '-draft' + outExt) :
                    rel.replaceAll(/\.[^.\/]+$/, outExt)
            allContent << content
        }
    }

    String extOf(String name) {
        def i = name.lastIndexOf('.')
        i < 0 ? '' : name.substring(i + 1).toLowerCase()
    }

    Map parseFile(File file, String ext) {
        def raw = file.getText('UTF-8')
        def content = [:]
        if (ext in ['ad', 'adoc', 'asciidoc']) {
            parseAsciidocHeader(raw, content)
            content.body = renderAsciidoc(file, raw)
        } else {
            // markdown / html: ~~~~~~ delimited header, verbatim keys
            def body = parseDelimitedHeader(raw, content)
            if (ext in ['md', 'markdown']) {
                content.body = renderMarkdown(body)
            } else {
                content.body = body
            }
        }
        if (content.tags instanceof String) {
            content.tags = ((String) content.tags).split(',').collect { it.trim() }
        }
        // jBake parses :jbake-date: into a Date using date.format (default yyyy-MM-dd)
        if (content.date instanceof String) {
            def fmt = (config?.date_format ?: 'yyyy-MM-dd').toString()
            try {
                content.date = new java.text.SimpleDateFormat(fmt).parse(((String) content.date).trim())
            } catch (ignored) { content.date = new Date(file.lastModified()) }
        }
        if (!content.date) content.date = new Date(file.lastModified())
        return content
    }

    // AsciiDoc: store every :jbake-x: under BOTH jbake-x and x (jBake behaviour).
    // Only the document *header* is scanned — the contiguous attribute block at
    // the top, ending at the first blank line. (jBake/Asciidoctor read header
    // attributes only, so example ":jbake-menu:" lines inside listing blocks
    // further down the document must NOT be mistaken for real headers.)
    void parseAsciidocHeader(String raw, Map content) {
        for (String line : raw.readLines()) {
            if (line.trim().isEmpty()) break          // end of header block
            def m = (line =~ /^:(jbake-[^:]+):\s*(.*)$/)
            if (m) {
                // Asciidoctor lowercases attribute names (case-insensitive), so
                // :jbake-rightColumnHtml: is stored as jbake-rightcolumnhtml.
                def full = ((String) m[0][1]).toLowerCase()
                def stripped = full.substring('jbake-'.length())
                // jBake reads substituted attribute values: Asciidoctor applies
                // its specialchars substitution (& < >). That is why e.g.
                // rightcolumn.gsp later un-escapes &lt;/&gt; in rightcolumnhtml.
                def value = escapeSpecialChars((String) m[0][2])
                content[full] = value
                content[stripped] = value
            }
        }
    }

    // Asciidoctor specialchars substitution (order matters: & first)
    String escapeSpecialChars(String s) {
        s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    }

    // Markdown/HTML: keys verbatim, body after the ~~~~~~ separator
    String parseDelimitedHeader(String raw, Map content) {
        if (!raw.contains('~~~~~~')) return raw
        def parts = raw.split('(?m)^~~~~~~\\s*$', 2)
        parts[0].eachLine { line ->
            def idx = line.indexOf('=')
            if (idx > 0) content[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return parts.length > 1 ? parts[1].replaceFirst(/^\r?\n/, '') : ''
    }

    String renderAsciidoc(File file, String text) {
        def attrs = AttributesBuilder.attributes(asciidoctorAttributes as String[]).get()
        def options = OptionsBuilder.options()
                .attributes(attrs)
                .safe(SafeMode.UNSAFE)
                .baseDir(file.parentFile)
                .get()
        return asciidoctor.convert(text, options)
    }

    String renderMarkdown(String md) {
        def exts = (config?.markdown_extensions ?: 'GITHUB,EXTRA,TABLES,TOC,FENCED_CODE_BLOCKS').toString()
        return MarkdownRenderer.toHtml(md, exts)
    }

    // ===================================================================
    //  Model lists (published_content, posts, tags, ...)
    // ===================================================================

    // jBake registers document types in this order (defaults first, then the
    // custom template.<type>.file types in jbake.properties order) and builds
    // published_content by iterating types, each "where status='published'
    // order by date desc". Content pages are independent of this ordering
    // (the menu re-sorts by order); it only affects sitemap.xml / lunrjsindex.js.
    static final List<String> DOCTYPE_ORDER = [
            'page', 'post', 'masterindex', 'archive', 'feed', 'tag', 'tagsindex',
            'sitemap', 'error', 'news', 'page_toc', 'page_tocl',
            'page_custom_menu', 'search', 'lunrjsindex',
    ]

    void buildModelLists() {
        def epoch = new Date(0)
        allContent.sort { a, b ->
            def ia = DOCTYPE_ORDER.indexOf(a.type as String); if (ia < 0) ia = DOCTYPE_ORDER.size()
            def ib = DOCTYPE_ORDER.indexOf(b.type as String); if (ib < 0) ib = DOCTYPE_ORDER.size()
            (ia <=> ib) ?:
                    (((b.date ?: epoch) <=> (a.date ?: epoch)) ?:
                            ((b.uri ?: '') <=> (a.uri ?: '')))
        }
    }

    List<Map> published() { allContent.findAll { it.status == 'published' } }

    List<Map> publishedPosts() {
        // jBake: "order by date desc"; equal dates fall back to insertion order
        // which, for the crawl, resolves to descending uri.
        published().findAll { it.type == 'post' }
                .sort { a, b ->
                    ((b.date ?: new Date(0)) <=> (a.date ?: new Date(0))) ?:
                            ((b.uri ?: '') <=> (a.uri ?: ''))
                }
    }

    Set<String> allTags() {
        // jBake: "select tags from post where status='published'" — posts only
        def tags = [] as TreeSet
        publishedPosts().each { c -> (c.tags ?: []).each { tags << it.toString().trim() } }
        return tags
    }

    // ===================================================================
    //  Render content
    // ===================================================================

    void renderAllContent() {
        allContent.each { content ->
            def template = typeTemplate[content.type]
            if (!template) {
                System.err.println "MicrositeBaker: no template for type '${content.type}' (${content.uri})"
                return
            }
            def model = baseModel(content)
            writePage(content.outputUri ?: content.uri, template, model)
        }
    }

    Map baseModel(Map content) {
        def posts = publishedPosts()
        // Render against a copy: templates (header.gsp, menu.groovy) mutate
        // content.rootpath/body/menu, and the page object is also an element of
        // published_content. jBake hands each template a fresh map from its
        // store, so those mutations must not leak into the shared lists.
        def model = [
                content          : new LinkedHashMap(content),
                config           : config,
                version          : version,
                published_content: published(),
                all_content      : allContent,
                published_posts  : posts,
                posts            : allContent.findAll { it.type == 'post' },
                published_date   : new Date(),
                alltags          : allTags(),
                tags             : allTags(),
        ]
        return model
    }

    void writePage(String uri, String template, Map model) {
        def outFile = new File(outputDir, uri)
        outFile.parentFile.mkdirs()
        outFile.withWriter('UTF-8') { w ->
            renderDocument(model, template, w)
        }
    }

    // ===================================================================
    //  Special pages: index, archive, feed, sitemap, tags
    // ===================================================================

    void renderSpecialPages() {
        // master index — needs a landing page (index.gsp includes doc/<landingPage>)
        if (!config.site_landingPage) {
            System.err.println "MicrositeBaker: no 'microsite.landingPage' configured in " +
                    "docToolchainConfig.groovy — skipping the landing page (index.html). " +
                    "Set e.g. microsite.landingPage = 'landingpage.gsp' and add that file to your theme's doc/ folder."
        } else {
            renderSpecial('masterindex', 'index.html', [type: 'masterindex', uri: 'index.html',
                                                         sourceuri: null, rootpath: null])
        }
        // archive (jBake default render.archive=true)
        if (boolProp('render_archive', true)) {
            renderSpecial('archive', 'archive.html', [type: 'archive', uri: 'archive.html',
                                                      sourceuri: null, rootpath: ''])
        }
        // feed
        if (boolProp('render_feed', true)) {
            renderSpecial('feed', config.feed_file.toString(),
                    [type: 'feed', uri: config.feed_file.toString(),
                     sourceuri: null, rootpath: ''])
        }
        // sitemap
        if (boolProp('render_sitemap', true)) {
            renderSpecial('sitemap', 'sitemap.xml', [type: 'sitemap', uri: 'sitemap.xml',
                                                     sourceuri: null, rootpath: ''])
        }
        // tags
        if (boolProp('render_tags', true)) {
            allTags().each { tag ->
                def safe = tag.replace(' ', '-')
                def uri = "tags/${safe}.html".toString()
                // jBake sets rootpath explicitly and leaves sourceuri null
                def content = [type: 'tag', uri: uri, sourceuri: null, rootpath: '../']
                def model = baseModel(content)
                model.tag = tag
                model.tag_posts = publishedPosts().findAll { (it.tags ?: []).contains(tag) }
                writePage(uri, typeTemplate['tag'], model)
            }
        }
    }

    void renderSpecial(String type, String uri, Map content) {
        def template = typeTemplate[type]
        if (!template) return
        def model = baseModel(content)
        writePage(uri, template, model)
    }

    boolean boolProp(String key, boolean dflt) {
        def v = config[key]
        v == null ? dflt : (v.toString() == 'true')
    }

    // ===================================================================
    //  Assets
    // ===================================================================

    void copyAssets() {
        // 1) the asset folder (assets/) -> output root
        if (assetFolder.exists()) {
            assetFolder.traverse(type: FileType.FILES) { f ->
                def rel = assetFolder.toPath().relativize(f.toPath()).toString()
                copyFile(f, new File(outputDir, rel))
            }
        }
        // 2) non-markup files in the content folder -> output (preserving path).
        //    Hidden files/dirs (.asciidoctorconfig, .asciidoctor/ diagram caches)
        //    are build artifacts and are not part of the site — skip them.
        contentFolder.traverse(type: FileType.FILES) { f ->
            def ext = extOf(f.name)
            if (ext in MARKUP_EXT) return
            def rel = contentFolder.toPath().relativize(f.toPath()).toString().replace('\\', '/')
            if (rel.split('/').any { it.startsWith('.') }) return
            copyFile(f, new File(outputDir, rel))
        }
    }

    void copyFile(File from, File to) {
        to.parentFile.mkdirs()
        to.bytes = from.bytes
    }

    // ===================================================================
    //  Markdown renderer — flexmark pegdown profile, exactly like jBake's
    //  MarkdownEngine (PegdownOptionsAdapter + the configured extensions).
    // ===================================================================
    static class MarkdownRenderer {
        static String toHtml(String md, String extensionsCsv) {
            def peg = Class.forName('com.vladsch.flexmark.parser.PegdownExtensions')
            int flags = 0
            // jBake's getMarkdownExtensions resolves each name against
            // PegdownExtensions and falls back to NONE for unknown ones
            // (e.g. GITHUB, EXTRA), so we silently skip those too.
            extensionsCsv.split(',').each { String name ->
                name = name.trim()
                if (!name) return
                try { flags |= peg.getField(name).getInt(null) } catch (ignored) { }
            }
            def adapter = Class.forName('com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter')
            def extType = Class.forName('com.vladsch.flexmark.util.misc.Extension')
            def emptyExt = java.lang.reflect.Array.newInstance(extType, 0)
            def pegOptions = adapter.getMethod('flexmarkOptions', int.class, emptyExt.getClass())
                    .invoke(null, flags, emptyExt)

            // jBake's output carries no heading ids — copy the pegdown profile but
            // turn header-id generation off so the HTML matches byte-for-byte.
            def mdsCls = Class.forName('com.vladsch.flexmark.util.data.MutableDataSet')
            def dataHolder = Class.forName('com.vladsch.flexmark.util.data.DataHolder')
            def mds = mdsCls.getConstructor(dataHolder).newInstance(pegOptions)
            def rendererCls = Class.forName('com.vladsch.flexmark.html.HtmlRenderer')
            def keyType = Class.forName('com.vladsch.flexmark.util.data.DataKey')
            ['GENERATE_HEADER_ID', 'RENDER_HEADER_ID'].each { k ->
                try {
                    def key = rendererCls.getField(k).get(null)
                    mdsCls.getMethod('set', keyType, Object).invoke(mds, key, Boolean.FALSE)
                } catch (ignored) { }
            }

            def parserCls = Class.forName('com.vladsch.flexmark.parser.Parser')
            def parser = parserCls.getMethod('builder', dataHolder).invoke(null, mds).build()
            def renderer = rendererCls.getMethod('builder', dataHolder).invoke(null, mds).build()
            def doc = parser.parse(md)
            return renderer.render(doc)
        }
    }
}
