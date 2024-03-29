package component

import base.SettingProperty
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.zeroturnaround.javarebel.conf.RebelPropertyResolver
import com.zeroturnaround.javarebel.idea.plugin.EmbeddedJRebelPath
import com.zeroturnaround.javarebel.idea.plugin.runner.JRebelDebugRunner
import com.zeroturnaround.javarebel.idea.plugin.xml.RebelXmlUtils
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

/**
 *
 * @author linxixin@cvte.com
 */

class InitStartupActivity : StartupActivity {

    companion object {
        private val log = Logger.getInstance(InitStartupActivity::class.java)
        val jrebelLocation = SettingProperty.getAdvanceLocation() + File.separator + "jrebel6" + File.separator + "jrebel.jar"


        init {
            //设置jrebel的javaagent包
            System.setProperty("griffin.jar.location", jrebelLocation)
            log.info("jrebel路径是在:" + SettingProperty.getAdvanceLocation() + File.separator + "jrebel6" + File.separator + "jrebel.jar")
            try {
                RebelPropertyResolver.add("rebel.license", SettingProperty.getAdvanceLocation() + File.separator + "jrebel6" + File.separator + "jrebel_test.lic")
            } catch (e: IOException) {
                log.error("rebel.license设置失败", e)
            }

            JRebelDebugRunner::class.java.name
            EmbeddedJRebelPath::class.java.name
        }
    }

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
        })
        /**
         * 自动插入rebel.xml文件以支持tomcat启动的时候能进行热部署
         */
        project.messageBus.connect().subscribe(BuildManagerListener.TOPIC, object : BuildManagerListener {

            override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
                super.buildFinished(project, sessionId, isAutomake)
                val modules = ModuleManager.getInstance(project).modules
                for (module in modules) {
                    val moduleOutputDirectory = CompilerPaths.getModuleOutputPath(module, false)
                    if (moduleOutputDirectory != null) {
                        val rebelXmlUtils = RebelXmlUtils(module)
                        val generateRebelXmlContents = rebelXmlUtils.javaClass.getDeclaredMethod("generateRebelXmlContentsIde")
                        generateRebelXmlContents.isAccessible = true
                        val rebelContent = generateRebelXmlContents.invoke(rebelXmlUtils)
                        val content = (rebelContent as String).replace(project.basePath!!, "\${myproject.root}")
                        val path = File(moduleOutputDirectory)

                        val file = File(path, "rebel.xml")
                        if (path.exists() && !file.exists()) {
                            BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), StandardCharsets.UTF_8)).use {
                                it.write(content)
                                it.flush()
                            }
                        }
                    }
                }
            }

        })

    }

}