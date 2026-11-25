# R8 / ProGuard rules for VitalFlow. Release builds shrink, rename and optimise the code.
#
# R8 only sees code that is called directly. Anything reached by NAME at runtime (JSON field names,
# Firestore property names, reflection) has to be kept here. Otherwise the release build silently
# renames or removes it while debug builds keep working.
# scripts/check_release_mapping.py runs in CI and fails the build if a model below is renamed or removed.

# Gson reads generic types and annotations from the class files.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Data models that are filled or read by reflection:
#  - Firestore maps documents to these classes through their getters and setters
#    (SyncRepository.restoreCollection, DocumentReference.set),
#  - Gson builds the Gemini request body and parses the Gemini response and HealthPlan.
# Everything in these packages is kept, including nested classes, fields, constructors and accessors.
# A new model belongs in data/entity (or data/entity/gemini), where it is covered automatically.
-keep class com.vitalflow.app.data.entity.** { *; }

# JSON result types declared inside GeminiRepository (parsed from the model's JSON reply with Gson).
# Move them to data/entity/gemini when GeminiRepository is split (Phase 5) and delete these three rules.
-keep class com.vitalflow.app.data.repository.GeminiRepository$FoodAnalysisResult { *; }
-keep class com.vitalflow.app.data.repository.GeminiRepository$PrescriptionResult { *; }
-keep class com.vitalflow.app.data.repository.GeminiRepository$VoiceAction { *; }

# Apache POI — optional desktop dependencies not present on Android
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.awt.Color
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Dimension2D
-dontwarn java.awt.geom.Path2D
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D$Double
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.IndexColorModel
-dontwarn java.awt.image.PackedColorModel
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn net.sf.saxon.Configuration
-dontwarn net.sf.saxon.dom.DOMNodeWrapper
-dontwarn net.sf.saxon.om.Item
-dontwarn net.sf.saxon.om.NamespaceUri
-dontwarn net.sf.saxon.om.NodeInfo
-dontwarn net.sf.saxon.om.Sequence
-dontwarn net.sf.saxon.om.SequenceTool
-dontwarn net.sf.saxon.sxpath.IndependentContext
-dontwarn net.sf.saxon.sxpath.XPathDynamicContext
-dontwarn net.sf.saxon.sxpath.XPathEvaluator
-dontwarn net.sf.saxon.sxpath.XPathExpression
-dontwarn net.sf.saxon.sxpath.XPathStaticContext
-dontwarn net.sf.saxon.sxpath.XPathVariable
-dontwarn net.sf.saxon.tree.wrapper.VirtualNode
-dontwarn net.sf.saxon.value.DateTimeValue
-dontwarn net.sf.saxon.value.GDateValue
-dontwarn org.apache.batik.anim.dom.SAXSVGDocumentFactory
-dontwarn org.apache.batik.bridge.BridgeContext
-dontwarn org.apache.batik.bridge.DocumentLoader
-dontwarn org.apache.batik.bridge.GVTBuilder
-dontwarn org.apache.batik.bridge.UserAgent
-dontwarn org.apache.batik.bridge.UserAgentAdapter
-dontwarn org.apache.batik.util.XMLResourceDescriptor
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
-dontwarn org.osgi.framework.wiring.BundleRevision
