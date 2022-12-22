/*
 	Author: Alexey Chichuk
	Description: Groovy for create allure-results for JMeter
	Date Create: 29.07.2021
	Date Update: 30.11.2022
	Version: 1.3.8
*/
	version = '1.3.8'

import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.util.Document;
import java.util.regex.Matcher;
import groovy.json.JsonSlurper;


/*
	Annotations AllureStory и AllureFeature, must be initialized ahead of time before tests or in
	start of controller. For example in JSR223 Sampler
	vars.put('AllureFeature','It is Allure Feature');
	vars.put('AllureStory','It is Allure Story');
*/

empty = ''
allureStepDisplayName = empty

//Vars for retry
sleepTimeForRetry = 1 * 1000 // (1000 ms in 1 second)
retryCount = 0
maxRetryCount = 1

machineName = JMeterUtils.getLocalHostFullName()
machineIp = JMeterUtils.getLocalHostIP()

// Not generate allure results files if toggle off and local
if ( (machineIp == "127.0.0.1" || machineName == "localhost") ){
	if (vars['logger.file.toggle'] == "off"){
		loggerFileToggle = "off"
	} else loggerFileToggle = "on"
} else loggerFileToggle = "on"


// AllureStory annotation
if (vars['AllureStory'] == null){
	allureStory = empty
}
else allureStory = vars['AllureStory']

// AllureManualID annotation
if (vars['AllureManualID'] == null || vars['AllureManualID'] == empty){
	allureManualID = empty
}
else allureManualID = '{' +
		'"name":"AS_ID",' +
		'"value":"' + vars['AllureManualID'] + '"' +
		'},'

// AllureFeature annotation
if (vars['AllureFeature'] == null){
	allureFeature = empty
}
else allureFeature = vars['AllureFeature']

// AllureEpic annotation
if (vars['AllureEpic'] == null){
	allureEpic = empty
	epicNameForFullName = empty
}
else {
	allureEpic = vars['AllureEpic']
	epicNameForFullName = allureEpic.toString().toLowerCase().replace(' ', '_') + '.'
}

// AllureCaseDescription annotation
if (vars['AllureCaseDescription'] == null){
	allureCaseDescription = empty
}
else allureCaseDescription = vars['AllureCaseDescription']

// AllureTestFragmentName annotation
if (vars['AllureTestFragmentName'] == null){
	allureTestFragmentName = empty
}
else allureTestFragmentName = vars['AllureTestFragmentName']

// AllureTestOwner annotation
if (vars['AllureTestOwner'] == null){
	allureTestOwner = empty
}
else allureTestOwner = vars['AllureTestOwner']

// AllureSeverity annotation
if (vars['AllureSeverity'] == null){
	allureSeverity = empty
}
else allureSeverity = vars['AllureSeverity']


/*
	SummaryCountTests
	PassedCountTests
	FailedCountTests
	SkippedCountTests
 */

// SummaryCountTests info
if (props['SummaryCountTests'] == null){
	summaryCountTests = 0
	passedCountTests = 0
	failedCountTests = 0
	skippedCountTests = 0
}
else {
	summaryCountTests = props['SummaryCountTests']
	passedCountTests = props['PassedCountTests']
	failedCountTests = props['FailedCountTests']
	skippedCountTests = props['SkippedCountTests']
}

/*
	A variable for storing the results of the run must be created in advance
	For example put it in User Defined Variables
	vars.put('_ALLURE_REPORT_PATH','/result/allure-results');
*/

allureReportPath = vars['_ALLURE_REPORT_PATH']

/*
	Declaring variables to create correct steps
*/

tags = empty
issues = empty

allureCaseFailReason = empty
critical = vars['critical']

if (vars['allureCaseFailReason'] == null){
	allureCaseFailReason = empty
} else allureCaseFailReason = vars['allureCaseFailReason']

if (vars['tags'] == null){
	tags = empty
} else tags = vars['tags']

if (vars['issues'] == null){
	issues = empty
} else issues = vars['issues']

stage = 'finished'
requestType = 'application/json'

AllurePrevMainSteps = vars['AllurePrevMainSteps']
allureStepResult = vars['allureStepResult']
allureCaseResult = vars['allureCaseResult']
responseData = prev.getResponseDataAsString()
threadName = prev.getThreadName().toString()
stepLog = '****'
allureLoggerInfo = stepLog + ' org.allure.reporter: '
SummarySubSteps = empty

/*
	Random UUID for creating attachments and result
	Unique enough so that test results do not overlap
*/

attachUUID 	= UUID.randomUUID().toString()

/*
	Vars for diff checker
*/

diffNameAttach = "Diff of expexted and actual"
diffPathAttach = attachUUID + "-diff-attachment"

/*
	Vars for original response if has retries
*/
responseOriginalNameAttach = empty
responseOriginalPathAttach = empty

/*
	If this is a test with a request to the database, then we take the entire context, if it is HTTP, then we only take the request
	Serves in order not to sacrifice domain cookies on the entire network and not to store them anywhere
*/

if (sampler.getClass().getName().contains('JDBC') || !sampler.getClass().getName().contains('HTTPSampler')){
	requestData = prev.getSamplerData()
	responseType = 'text/plain'
	requestType = 'text/plain'
} else requestData = sampler.getUrl().toString() + '\n\n' +
	prev.getRequestHeaders().replaceAll(/authorization:.*/,"authorization: XXX (Has been replaced for safety)") + 
	'\n' + prev.getHTTPMethod() + ":" + '\n' + prev.getQueryString();

if ( prev.getResponseHeaders().contains('text/html') ){
	responseType = 'text/html'
} else if ( prev.getResponseHeaders().contains('image/png') ){
	responseType = 'image/png'
	vars.put('pngUUID', empty)
	vars.put('pngUUID', attachUUID)
} else if ( prev.getResponseHeaders().contains('application/pdf') ){
	responseType = 'application/pdf'
	vars.put('attachment-UUID', empty)
	vars.put('attachment-UUID', attachUUID)
} else {
	vars.put('attachment-UUID', attachUUID)
	responseType = requestType 
} 

/*
	To use tika lib, for example download and assert something in xlxs/xml just use parameter
	tika_xml (if you have multiples steps, put it with space (for example: 'start tika_xml')
*/

if (Parameters.contains('tika_xml')) {
	byte [] samplerdata = ctx.getPreviousResult().getResponseData()
	String converted = Document.getTextFromDocument(samplerdata)
	if ((m = (converted =~ /sharedStrings.xml\n(.*)/))) {
		responseData = m[0].toString()
	}
    else responseData = converted.toString()
}

if (Parameters.contains('tags=')) {
	if (Parameters =~ ~/tags=\[(.+?)\]/) {
		def tags_memory = Matcher.lastMatcher[0][1].split(',')
		println (tags_memory.size());
		for(int i = 0; i < tags_memory.size(); i++) {
	        tags += '{' +
				'"name":"tag",' +
				'"value":"' + tags_memory[i] + '"' +
				'},'
			vars.put('tags', tags)
			}
		}
}

if (Parameters.contains('issues=')) {
	if (Parameters =~ ~/issues=\[(.+?)\]/) {
		def issues_memory = Matcher.lastMatcher[0][1].split(',')
		for(int i = 0; i < issues_memory.size(); i++) {
			issues += '{' +
					'"name":"issue",' +
					'"value":"' + issues_memory[i] + '"' +
					'},'
			vars.put('issues', issues)
		}
	}
}


if ((!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start'))){
	allureDisplayName = sampler.getName()
} else allureDisplayName = vars['AllureCaseName']


void loop(list, key, value) {
	if(value.class == null) {
		if(value != null && !value.isEmpty()) {
			value.each { k, v ->
				loop(list, (key + '.' + k), v)
			}
		} else {
			//log.warn(key + ": empty")
			list.add((key))
		}
	} else if(value.class == ArrayList) {
		value.each {
			loop(list, key, it)
		}
	} else {
		//log.info(key + " = " + value)
		list.add(key + " = " + value)
	}
}

/*
	Create full name of case for allure history
*/

allureFullName = 'org.jmeter.com.' + epicNameForFullName + allureFeature.toString().toLowerCase().replace(' ',
		'_') + '.' + allureStory.toString().toLowerCase().replace(' ',
		'_') + '.' + allureDisplayName.toString().toLowerCase().replace(' ','_')


/*
	Func for adding all steps
*/
void addAllSteps() {
	int countAssertions = prev.getAssertionResults().size().toInteger();
	vars.putObject("countAssertions", countAssertions)

	for (i = 0; i < countAssertions; i++) {

		assertionResult = prev.getAssertionResults()[i]

		log.info("Status code is: " + prev.getResponseCode() + " is OK?...")
		if (assertionResult.isFailure()) {
			log.info(allureLoggerInfo + (stepLog * i) + '[' + i + '] Step: ' + prev
					.getAssertionResults()[i].toString() + ': failed; reason: '
					+ assertionResult.getFailureMessage().toString())

			allureStepDisplayName = prev.getAssertionResults()[i].toString()
			isStepNeedToRetry()

			allureStepFailReason = assertionResult.getFailureMessage().toString()
			allureMainFailReason = '[Sample: ' + sampler.getName()+ ' in sub step: ' +
					allureStepDisplayName + ' failed with reason: ' + assertionResult
					.getFailureMessage().toString() + ']' + '\\' + 'n'

			allureCaseFailReason = allureCaseFailReason + allureMainFailReason
			vars.put('allureCaseFailReason', allureCaseFailReason)
			allureCaseResult = 'failed'
			vars.put('allureCaseResult', 'failed')
			allureStepResult = 'failed'
			/*
				Json diff checker
			*/
			if ( prev.getResponseHeaders().contains('application/json') &&  allureStepFailReason.contains('{') &&  allureStepFailReason.contains('}')){
				def listA = []
				def listB = []
				def expectedDataString = allureStepFailReason.substring(0, allureStepFailReason.lastIndexOf("]', but found '")).replace("Value expected to be '[", "")
				def actualDataString = allureStepFailReason.replaceAll(".+]', but found '.", "").replace("]'", "");

				def expectedData = new JsonSlurper().parseText(expectedDataString)
				def actualData = new JsonSlurper().parseText(actualDataString)

				expectedData.each { key, value ->
					loop(listA, key, value)
				}

				actualData.each { key, value ->
					loop(listB, key, value)
				}

				diffOfActualDataAndExpectedData = ("Expected: " + listA.minus(listB) + '\n' + "Actual: " + listB.minus(listA))
				allureStepFailReason = diffOfActualDataAndExpectedData

				var diff = new PrintWriter(allureReportPath + '/' + attachUUID + '-diff-attachment')
				diff.write(diffOfActualDataAndExpectedData)
				diff.close()
			}else {
				diffNameAttach = empty
				diffPathAttach = empty
			}

			if ( (retryCount > maxRetryCount) || (isRetry == false) ) {
				addMoreSubStep()
			}
		}

		if (!assertionResult.isFailure()) {
			log.info(allureLoggerInfo + (stepLog * i) + '[' + i + '] Step: ' + prev
					.getAssertionResults()[i].toString()
					+ ': passed')
			allureStepDisplayName = prev.getAssertionResults()[i].toString()
			allureStepResult = 'passed'
			allureStepFailReason = empty
			diffNameAttach = empty
			diffPathAttach = empty
			addMoreSubStep()
		}
	}
	if (countAssertions == 0) {
		diffNameAttach = empty
		diffPathAttach = empty
	}
}

/*
	isStepNeedToRetry
*/
void isStepNeedToRetry(){

	retryCount = retryCount + 1
	/*
		1st condition for retry: status code ~5XX
	 */
	if ( (prev.getResponseCode() =~  '5..' || (prev.getResponseCode() =~ '400' && (sampler.getUrl().toString().contains('rabbitry') || sampler.getUrl().toString().contains('cs-colvir-helper')) )) && retryCount == maxRetryCount){

		isRetry = true

		allureStepDisplayName = "[With retry]: " + prev.getAssertionResults()[0].toString()
		// Write original response
		responseOriginalNameAttach = "Original response before retry"
		responseOriginalPathAttach = attachUUID + "-response-original-attachment"

		var responseOriginal = new PrintWriter(allureReportPath + '/' + attachUUID + '-response-original-attachment')
		responseOriginal.write(prev.getResponseDataAsString())
		responseOriginal.close()

		sleep(sleepTimeForRetry)

		if (sampler.getUrl().toString().contains('rabbitry')){
			new_request = prev.getQueryString().toString().replaceAll("[\\t\\n\\r]+","")

			// For new version rabbitry (auto-retry and rebuild)
			data = new org.apache.jmeter.config.Arguments()
			body = new org.apache.jmeter.protocol.http.util.HTTPArgument('', new_request, '', false)
			body.setAlwaysEncoded(false)
			data.addArgument(body)
			sampler.setArguments(data)
		}

		// Change context
		retry = sampler.sample(null)

		assertionResultRetry = prev.getAssertionResults()[0]
		log.info("Status code is: " + retry.getResponseCode() + " doing retry...")

		// Change all prev.results to new context
		prev.addSubResult(retry, false)
		prev.setResponseData(retry.getResponseData())
		prev.setContentType(retry.getContentType())
		prev.setResponseCode(retry.getResponseCode())
		prev.setResponseHeaders(retry.getResponseHeaders())
		prev.setResponseMessage(retry.getResponseMessage())
		prev.setResponseData(retry.getResponseData())

		responseData = retry.getResponseDataAsString()

		if (retry.isSuccessful()){
			prev.setResponseOK()
			allureStepFailReason = empty
			allureStepResult = 'passed'
			allureStepFailReason = empty
			diffNameAttach = empty
			diffPathAttach = empty
			addMoreSubStep()
		} else {
			addAllSteps()
		}

	} else {
		isRetry = false
	}
}

/*
	Case is starting
 */

if (( !Parameters.contains('stop') && !Parameters.empty && Parameters.contains('start') )) {
	vars.put('caseTimeStart',prev.getStartTime().toString())
	addAllSteps()
	addMoreMainStep(false)
}

/*
	Case continue
*/

else if ( (Parameters.contains('stop') && !Parameters.empty || Parameters.contains('continue') )) {
	addAllSteps()
	addMoreMainStep(true)
}

/*
	Case with single step
*/

else if (!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start')) {
	vars.put('allureCaseResult', 'passed')
	vars.put('AllurePrevMainSteps', empty)
	if (sampler.getComment() != null) {
		allureCaseDescription = sampler.getComment()
	} else allureCaseDescription = empty
	vars.put('caseTimeStart',prev.getStartTime().toString())
	addAllSteps()
	addMoreMainStep(false)
}

else {
	throw new Exception ("ERROR: Oops... Something is going wrong")
};

/*
	Func for adding sub steps
*/

def addMoreSubStep(){
	if (!SummarySubSteps.empty) SummarySubSteps = SummarySubSteps + ','

	String SubStep = '{' +
			'"name":"'+ allureStepDisplayName.toString() + '",' +
				'"status":"' + allureStepResult + '",' +
				'"stage":"'+ stage +'",' +
				'"statusDetails":' +
					'{' +
						'"message":"' + allureStepFailReason.replace("\"", "\'").replace("\\", "\\\\").replace("\n", " ").replace("\t", " ")  + '"' +
					'}' +
			'}'

	SummarySubSteps = SummarySubSteps + SubStep
	vars.put('SummarySubSteps',SummarySubSteps)

}

/*
	Func for adding main steps
*/

def addMoreMainStep(boolean addPoint){

	if (SummarySubSteps.contains('"status":"failed"')) allureStepResult = 'failed'
		else allureStepResult = 'passed'

	if (AllurePrevMainSteps == null) AllurePrevMainSteps = empty
	if (addPoint == true && AllurePrevMainSteps != empty) AllurePrevMainSteps = AllurePrevMainSteps +
			','

	vars.put('attachUUID', attachUUID)
	String StepL = '{' +
			'"name":"' + sampler.getName().replace("\"", "\'") + '",' +
			'"status":"' + allureStepResult + '",' +
			'"stage":"'+ stage +'",' +
			'"steps":' +
				'[' +
					SummarySubSteps +
				'],' +
			'"statusDetails": {"message":"' + empty + '"},' +
			'"attachments":' +
				'[' +
					'{' +
						'"name":"Request",' +
						'"source":"' + vars['attachUUID'] + '-request-attachment",' +
						'"type":"'+ requestType +'"' +
					'},' +
					'{' +
						'"name":"Response",' +
						'"source":"' + vars['attachUUID'] + '-response-attachment",' +
						'"type":"'+ responseType +'"' +
					'},' +
					'{' +
						'"name":"'+ responseOriginalNameAttach + '",' +
						'"source":"' + responseOriginalPathAttach + '",' +
						'"type":"'+ responseType +'"' +
					'},' +
					'{' +
						'"name":"'+ diffNameAttach + '",' +
						'"source":"' + diffPathAttach + '",' +
						'"type":"text/plain"' +
					'}' +
			'],' +
			'"start":"' + prev.getStartTime().toString() + '",' +
			'"stop":"' + prev.getEndTime().toString() + '"' +
			'}'

	AllurePrevMainSteps =  AllurePrevMainSteps + StepL

/*
	If one of step is failed = case is failed
*/

	if (AllurePrevMainSteps.contains('"status":"failed"')) {
		allureCaseResult = 'failed'
		if (!Parameters.contains('skipped')) {
			failedCountTests += 1 //Up count of failed
		}
	}
	else {
		allureCaseResult = 'passed'
		if (!Parameters.contains('skipped')) {
			//Up count of failed
			passedCountTests += 1
		}
	}

	if (Parameters.contains('skipped')) {
		allureCaseResult = 'skipped'
		skippedCountTests += 1	//Up count of skipped
	}

	if (Parameters.contains('critical') && allureStepResult == 'failed'){
		critical = 'yes'
		vars.put('critical', 'yes')}

	String AResult = empty +
			'{"name":"' + allureDisplayName.replace("\"", "\'") + '",' +
			'"description":"' + allureCaseDescription.replace("\"", "\'") + '",' +
			'"status":"' + allureCaseResult + '",' +
			'"statusDetails":' +
				'{' +
					'"message":"'+ allureCaseFailReason.replace("\"", "\'").replace("\\", "\\\\").replace("\n", " ").replace("\t", " ")  + '"' +
				'},' +
			'"stage":"' + stage + '",' +
			'"steps":' +
				'[' +
					AllurePrevMainSteps +
				'],' +
			'"start":' + vars['caseTimeStart'] + ',' +
			'"stop":' + prev.getEndTime()+',' +
			'"uuid":"' + attachUUID+'","historyId":"' + attachUUID + '",' +
			'"fullName":"' + allureFullName + '",' +
			'"labels":[' +
					'{' +
						'"name":"framework",' +
						'"value":"jmeter"' +
					'},' +
					tags +
					issues +
					'{' +
						'"name":"host",' +
						'"value":"' + threadName + '"' +
					'},'+
					'{' +
						'"name":"machineName",' +
						'"value":"' + machineName + '"' +
					'},'+
					'{' +
						'"name":"ip",' +
						'"value":"' + machineIp + '"' +
					'},'+
					'{' +
						'"name":"testFragment",' +
						'"value":"' + allureTestFragmentName + '"' +
					'},'+
					'{' +
						'"name":"owner",' +
						'"value":"' + allureTestOwner + '"' +
					'},'+
					'{' +
					'"name":"severity",' +
					'"value":"' + allureSeverity + '"' +
					'},'+
					'{' +
						'"name":"layer",' +
						'"value":"jmeter"' +
					'},'+
					allureManualID + 
					'{' +
						'"name":"language",' +
						'"value":"java"' +
					'},'+
					'{' +
						'"name":"epic",' +
						'"value":"' + allureEpic + '"' +
					'},' +
					'{' +
						'"name":"story",' +
						'"value":"' + allureStory + '"' +
					'},' +
					'{' +
						'"name":"feature",' +
						'"value":"' + allureFeature + '"' +
				'}' +
			'],' +
			'"links":[]}'

	vars.put('AllurePrevMainSteps', AllurePrevMainSteps)
	vars.put('AResult', AResult)
}


/*
	Write attachments to files (request/response)
*/

if(!Parameters.contains('no_report') && loggerFileToggle == "on"){
	var request = new PrintWriter(allureReportPath + '/' + attachUUID + '-request-attachment')
	request.write(requestData)
	request.close()

	var response = new PrintWriter(allureReportPath + '/' + attachUUID + '-response-attachment')
	if ( responseType != 'image/png' ){
		response.write(responseData);
	}
	response.close()
}

/*
	Write result to file if case  end
*/

if ((!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start') && !Parameters.contains('no_report')) ||
		Parameters.contains('stop') && loggerFileToggle == "on"  && !Parameters.contains('no_report')) {
	var result = new PrintWriter(allureReportPath  +'/' + attachUUID + '-result.json')
	result.write(vars['AResult'])
	result.close()

	if (summaryCountTests == 0){
		println(('===='*20) + '\n' + (' '*30)+"Allure-reporter: v" + version + '\n' + ('===='*20))
	}
	println('ALLURE_CASE_RESULT: ' + allureFullName + ': ' + allureCaseResult.toUpperCase())
	
	//Up count of summary
	summaryCountTests += 1
	
	vars.put('AllurePrevMainSteps', empty)
	vars.put('AllureEpic', null)
	vars.put('AResult', empty)
	vars.put('SummarySubSteps', empty)
	vars.put('critical', empty)
	vars.put('allureCaseResult', 'passed')
	vars.put('allureCaseFailReason', empty)
	vars.put('tags', empty)
	vars.put('issues', empty)
	vars.put('AllureCaseDescription', empty)
	vars.put('AllureManualID', empty)
	vars.put('AllureTestFragmentName', empty)
	vars.put('AllureSeverity', empty)

	props.put('SummaryCountTests', summaryCountTests)
	props.put('PassedCountTests', passedCountTests)
	props.put('FailedCountTests', failedCountTests)
	props.put('SkippedCountTests', skippedCountTests)
		
		log.info("************ SummaryCountTests: " + summaryCountTests)
		log.info("************ PassedCountTests: " + passedCountTests)
		log.info("************ FailedCountTests: " + failedCountTests)
		log.info("************ SkippedCountTests: " + skippedCountTests)
		log.info("************ Success Rate: " + passedCountTests/summaryCountTests * 100 + " %")
	

	if (critical == 'yes') {
		println("CRITICAL_TEST_CASE_FAILED: " + allureFullName + " FAILED!");
		println("STOPPING....");
		prev.setStopThread(true);
	}
}
