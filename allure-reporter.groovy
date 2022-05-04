/*
 	Author: Alexey Chichuk
	Description: Groovy for create allure-results for JMeter
	Date Create: 29.07.2021
	Date Update: 28.04.2022
	Version: 1.2.4
*/

import org.apache.jmeter.util.Document;
import java.util.regex.Matcher;


/*
	Annotations AllureStory и AllureFeature, must be initialized ahead of time before tests or in
	start of controller. For example in JSR223 Sampler
	vars.put('AllureFeature','It is Allure Feature');
	vars.put('AllureStory','It is Allure Story');
*/

empty = ''

// AllureStory annotation
if (vars['AllureStory'] == null){
	allureStory = empty
}
else allureStory = vars['AllureStory']

// AllureManualID annotation
if (vars['AllureManualID'] == null){
	allureManualID = empty
}
else allureManualID = vars['AllureManualID']

// AllureFeature annotation
if (vars['AllureFeature'] == null){
	allureFeature = empty
}
else allureFeature = vars['AllureFeature']

// AllureCaseDescription annotation
if (vars['AllureCaseDescription'] == null){
	allureCaseDescription = empty
}
else allureCaseDescription = vars['AllureCaseDescription']

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
	skippedCountTests =props['SkippedCountTests']
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
}
else allureCaseFailReason = vars['allureCaseFailReason']

if (vars['tags'] == null){
	tags = empty
}
else tags = vars['tags']

if (vars['issues'] == null){
	issues = empty
}
else issues = vars['issues']

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
	If this is a test with a request to the database, then we take the entire context, if it is HTTP, then we only take the request
	Serves in order not to sacrifice domain cookies on the entire network and not to store them anywhere
*/

if (sampler.getClass().getName().contains('JDBC')){
	requestData = prev.getSamplerData()
	responseType = 'text/plain'
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
}else responseType = requestType 

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
		println (issues_memory.size());
		for(int i = 0; i < issues_memory.size(); i++) {
			issues += '{' +
					'"name":"issue",' +
					'"value":"' + issues_memory[i] + '"' +
					'},'
			println(issues_memory[i])
			println(issues)
			vars.put('issues', issues)
		}
	}
}


if ((!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start'))){
	allureDisplayName = sampler.getName()
} else allureDisplayName = vars['AllureCaseName']

/*
	Create full name of case for allure history
*/
allureFullName = 'org.jmeter.com.' + allureFeature.toString().toLowerCase().replace(' ',
		'_') + '.' + allureStory.toString().toLowerCase().replace(' ',
		'_') + '.' + allureDisplayName.toString().toLowerCase().replace(' ','_')

/*
	Func for adding all steps
*/
void addAllSteps() {
	int countAssertions = SampleResult.getAssertionResults().size().toInteger();
	vars.putObject("countAssertions", countAssertions)

	for (i = 0; i < countAssertions; i++) {

		assertionResult = SampleResult.getAssertionResults()[i]
		allureStepDisplayName = SampleResult.getAssertionResults()[i].toString();

		if (assertionResult.isFailure()) {
			log.info(allureLoggerInfo+ (stepLog * i) + '[' + i + '] Step: ' + SampleResult
					.getAssertionResults()[i].toString() + ': failed; reason: '
					+ assertionResult.getFailureMessage().toString())
			allureStepFailReason = assertionResult.getFailureMessage().toString()
			allureMainFailReason = '[Sample: ' + sampler.getName()+ ' in sub step: ' +
					allureStepDisplayName + ' failed with reason: ' + assertionResult
					.getFailureMessage().toString() + ']' + '\\' + 'n'

			allureCaseFailReason = allureCaseFailReason + allureMainFailReason
			vars.put('allureCaseFailReason', allureCaseFailReason)
			allureCaseResult = 'failed'
			vars.put('allureCaseResult', 'failed')
			allureStepResult = 'failed'
			addMoreSubStep()
		}

		if (!assertionResult.isFailure()) {
			log.info(allureLoggerInfo+ (stepLog * i) + '[' + i + '] Step: ' + SampleResult
					.getAssertionResults()[i].toString()
					+ ': passed')
			allureStepResult = 'passed'
			allureStepFailReason = empty
			addMoreSubStep()
		}
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
					'{"name":"Response",' +
					'"source":"' + vars['attachUUID'] + '-response-attachment",' +
					'"type":"' + responseType + '"' +
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
			//Up count of failed
			failedCountTests += 1
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
		//Up count of skipped
		skippedCountTests += 1
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
						'"name":"layer",' +
						'"value":"jmeter"' +
					'},'+
					'{' +
						'"name":"AS_ID",' +
						'"value":"' + allureManualID + '"' +
					'},'+
					'{' +
						'"name":"language",' +
						'"value":"java"' +
					'},'+
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

var request = new PrintWriter(allureReportPath + '/' + attachUUID + '-request-attachment')
	request.write(requestData)
	request.close()

var response = new PrintWriter(allureReportPath + '/' + attachUUID + '-response-attachment')
	if ( responseType != 'image/png' ){
		response.write(responseData);
	}
	response.close()

/*
	Write result to file if case  end
*/

if ((!Parameters.contains('stop') && !Parameters.contains('continue')  && !Parameters.contains('start')) || Parameters.contains('stop')) {
	println('ALLURE_CASE_RESULT: ' + allureFullName + ': ' + allureCaseResult.toUpperCase())
	var result = new PrintWriter(allureReportPath + '/' + attachUUID + '-result.json')
	result.write(vars['AResult'])
	result.close()

	//Up count of summary
	summaryCountTests += 1
	
	vars.put('AllurePrevMainSteps', empty)
	vars.put('AResult', empty)
	vars.put('SummarySubSteps', empty)
	vars.put('critical', empty)
	vars.put('allureCaseResult', 'passed')
	vars.put('allureCaseFailReason', empty)
	vars.put('tags', empty)
	vars.put('issues', empty)
	vars.put('AllureCaseDescription', empty)
	vars.put('AllureManualID', empty)
	

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
