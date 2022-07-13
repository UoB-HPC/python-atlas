var merge = require('webpack-merge');
var generated = require('./scalajs.webpack.config');
var path = require('path');

var local = {
    devtool: false,
    module: {
        rules: [

            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader']
            },
            {
                test: /\.(ttf|eot|woff|svg|woff2|png|glb|jpeg|jpg|mp4|jsn)$/,
                use: 'file-loader'
            },
            {
                test: /\.(eot)$/,
                use: 'url-loader'
            }
        ]
    }
};

module.exports = merge(generated, local);