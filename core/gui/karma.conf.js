// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

module.exports = function(config) {
  config.set({
    basePath: "",
    frameworks: ['waitwebpack', "jasmine", "@angular-devkit/build-angular"],
    plugins: [
      require("karma-jasmine"),
      require("karma-chrome-launcher"),
      require("karma-coverage"),
      require("karma-jasmine-html-reporter"),
      require('./karma.waitwebpack'),
      require("@angular-devkit/build-angular/plugins/karma")
    ],
    client: {
      clearContext: false, // leave Jasmine Spec Runner output visible in browser
      jasmine: {
        random: false // disable the random running order
      }
    },
    customLaunchers: {
      ChromeHeadlessCustom: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--headless',
          '--disable-gpu',
          '--disable-translate',
          '--disable-extensions'
        ],
      },
    },
    reporters: ["progress", "kjhtml"],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ["ChromeHeadlessCustom"],
    singleRun: false,
    captureTimeout: 240000,
    browserDisconnectTimeout: 60000,
    browserDisconnectTolerance: 1,
    browserNoActivityTimeout: 240000
  });
};
