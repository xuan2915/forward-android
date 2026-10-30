package com.focus.prototype
import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
class BusinessChecks:Instrumentation() {
 private var serviceStage=""
 override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);serviceStage=arguments?.getString("service-stage").orEmpty();start() }
 override fun onStart() {
  val output=Bundle()
  try {if(serviceStage.isNotEmpty()){output.putString("stream",ServiceFixtureSetup(targetContext).run(serviceStage))}else{val checks=runBusinessChecks(targetContext);output.putString("stream","PASS ${checks.size} business flows\n"+checks.joinToString("\n"))};finish(Activity.RESULT_OK,output)}
  catch(error:Throwable){output.putString("stream","FAIL: ${android.util.Log.getStackTraceString(error)}");finish(Activity.RESULT_CANCELED,output)}
 }
}
