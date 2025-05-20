'use strict';

/**
 * Document translate modal controller.
 */
angular.module('docs').controller('DocumentModalTranslate', function($scope, $uibModalInstance, Restangular, document, $httpParamSerializerJQLike) {
    $scope.document = document;
    $scope.translating = false;
    $scope.error = null;
    $scope.contentType = 'description';
    $scope.translatedContent = '';

    // Available languages for translation
    $scope.languages = [
        { code: 'en', name: 'English' },
        { code: 'zh', name: 'Chinese' },
        { code: 'ja', name: 'Japanese' },
        { code: 'ko', name: 'Korean' },
        { code: 'fr', name: 'French' },
        { code: 'de', name: 'German' },
        { code: 'es', name: 'Spanish' },
        { code: 'ru', name: 'Russian' }
    ];

    // 默认选择中文作为目标语言
    $scope.targetLanguage = 'zh';

    /**
     * Translate the document.
     */
    $scope.translate = function() {
        $scope.translating = true;
        $scope.error = null;
        $scope.translatedContent = '';

        var data = {
            targetLanguage: $scope.targetLanguage,
            contentType: $scope.contentType
        };

        // 使用customPOST并强制表单格式
        Restangular.one('document', document.id).all('translate').customPOST(
            $httpParamSerializerJQLike(data),
            '', // route
            {}, // params
            {'Content-Type': 'application/x-www-form-urlencoded'}
        ).then(function(response) {
            if (response && response.translated) {
                $scope.translatedContent = response.translated;
            } else {
                $scope.error = 'No translation received from server';
            }
        }).catch(function(error) {
            // 兼容后端各种错误结构
            if (error && error.data) {
                $scope.error = error.data.message || error.data.error || 'An error occurred during translation';
            } else if (error && error.statusText) {
                $scope.error = error.statusText;
            } else {
                $scope.error = 'An error occurred during translation';
            }
        }).finally(function() {
            $scope.translating = false;
        });
    };
});